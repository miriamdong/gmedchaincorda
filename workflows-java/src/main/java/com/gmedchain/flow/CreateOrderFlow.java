package com.gmedchain.flow;

import com.gmedchain.state.OrderState;
import com.gmedchain.contract.OrderContract;
import co.paralleluniverse.fibers.Suspendable;
import com.gmedchain.types.Types;
import com.google.common.collect.ImmutableSet;
import net.corda.core.contracts.Command;
import net.corda.core.contracts.UniqueIdentifier;
import net.corda.core.flows.*;
import net.corda.core.identity.Party;
import net.corda.core.transactions.SignedTransaction;
import net.corda.core.transactions.TransactionBuilder;
import net.corda.core.utilities.ProgressTracker;

public class CreateOrderFlow {

    @InitiatingFlow
    @StartableByRPC
    public static class Initiator extends FlowLogic<SignedTransaction> {
        private final String productName;
        private final String productSKU;
        private final float productPrice;
        private final int productQty;
        private final float shippingCost;
        private final Party seller;
        private final Party shipper;
        private final String buyerAddress;
        private final String sellerAddress;
        private final Types.OrderTypes status;

        private final ProgressTracker.Step GENERATING_TRANSACTION = new ProgressTracker.Step("Generating transaction based on new Order.");
        private final ProgressTracker.Step VERIFYING_TRANSACTION = new ProgressTracker.Step("Verifying contract constraints.");
        private final ProgressTracker.Step SIGNING_TRANSACTION = new ProgressTracker.Step("Signing transaction with our private key.");
        private final ProgressTracker.Step FINALISING_TRANSACTION = new ProgressTracker.Step("Obtaining notary signature and recording transaction.") {
            @Override
            public ProgressTracker childProgressTracker() {
                return FinalityFlow.Companion.tracker();
            }
        };

        // The progress tracker checkpoints each stage of the flow and outputs the specified messages when each
        // checkpoint is reached in the code. See the 'progressTracker.currentStep' expressions within the call()
        // function.
        private final ProgressTracker progressTracker = new ProgressTracker(
                GENERATING_TRANSACTION,
                VERIFYING_TRANSACTION,
                SIGNING_TRANSACTION,
                FINALISING_TRANSACTION
        );

        public Initiator(String productSKU, String productName, float productPrice, int productQty, float shippingCost, Types.OrderTypes status,
                         Party seller, Party shipper, String buyerAddress, String sellerAddress) {
            this.productQty = productQty;
            this.productSKU = productSKU;
            this.productName = productName;
            this.productPrice = productPrice;
            this.shippingCost = shippingCost;
            this.status = status;
            this.shipper = seller;
            this.seller = shipper;
            this.buyerAddress = buyerAddress;
            this.sellerAddress = sellerAddress;
        }

        @Override
        public ProgressTracker getProgressTracker() {
            return progressTracker;
        }

        /**
         * The flow logic is encapsulated within the call() method.
         */
        @Suspendable
        @Override
        public SignedTransaction call() throws FlowException {
            // Obtain a reference to the notary we want to use.
            final Party notary = getServiceHub().getNetworkMapCache().getNotaryIdentities().get(0);

            // Stage 1.
            progressTracker.setCurrentStep(GENERATING_TRANSACTION);
            // Generate an unsigned transaction.
            Party me = getOurIdentity();
            OrderState orderState = new OrderState(productSKU, productName, productPrice, productQty, shippingCost,
                                                   me, seller, shipper, buyerAddress, sellerAddress, new UniqueIdentifier());

            final Command<OrderContract.Commands.Create> txCommand = new Command<>(
                    new OrderContract.Commands.Create(),
                    me.getOwningKey());
            final TransactionBuilder txBuilder = new TransactionBuilder(notary)
                    .addOutputState(orderState, OrderContract.ID)
                    .addCommand(txCommand);

            // Stage 2.
            progressTracker.setCurrentStep(VERIFYING_TRANSACTION);
            // Verify that the transaction is valid.
            txBuilder.verify(getServiceHub());

            // Stage 3.
            progressTracker.setCurrentStep(SIGNING_TRANSACTION);
            // Sign the transaction.
            final SignedTransaction signedTx = getServiceHub().signInitialTransaction(txBuilder);

            // Send the state to the counterpart, and receive it back with their signature.
            FlowSession sellerPartySession = initiateFlow(seller);
            FlowSession shipperPartySession = initiateFlow(shipper);

            // Stage 5.
            progressTracker.setCurrentStep(FINALISING_TRANSACTION);
            // Notarise and record the transaction in both parties' vaults.
            subFlow(new FinalityFlow(signedTx, ImmutableSet.of(sellerPartySession)));

            return subFlow(new FinalityFlow(signedTx, ImmutableSet.of(shipperPartySession)));
        }
    }

    @InitiatedBy(com.example.flow.ExampleFlow.Initiator.class)
    public static class Acceptor extends FlowLogic<SignedTransaction> {

        private final FlowSession otherPartySession;

        public Acceptor(FlowSession otherPartySession) {
            this.otherPartySession = otherPartySession;
        }

        @Suspendable
        @Override
        public SignedTransaction call() throws FlowException {
            return subFlow(new ReceiveFinalityFlow(otherPartySession));
        }
    }
}