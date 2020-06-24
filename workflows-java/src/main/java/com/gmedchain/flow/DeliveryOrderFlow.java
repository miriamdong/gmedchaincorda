package com.gmedchain.flow;

import co.paralleluniverse.fibers.Suspendable;
import com.gmedchain.common.Order;
import com.google.common.collect.ImmutableSet;
import net.corda.core.contracts.UniqueIdentifier;
import net.corda.core.contracts.Command;
import net.corda.core.contracts.UniqueIdentifier;
import net.corda.core.flows.*;
import net.corda.core.identity.AbstractParty;
import net.corda.core.identity.Party;
import net.corda.core.transactions.SignedTransaction;
import net.corda.core.transactions.TransactionBuilder;
import net.corda.core.utilities.ProgressTracker;
import com.gmedchain.state.OrderState;
import com.gmedchain.contract.OrderContract;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class DeliveryOrderFlow {

    @InitiatingFlow
    @StartableByRPC
    public static class Initiator extends FlowLogic<UniqueIdentifier> {
        private final Order order;
        private final Party buyer;
        private final Party seller;

        private final ProgressTracker.Step GENERATING_TRANSACTION = new ProgressTracker.Step("Generating transaction based on new IOU.");
        private final ProgressTracker.Step VERIFYING_TRANSACTION = new ProgressTracker.Step("Verifying contract constraints.");
        private final ProgressTracker.Step SIGNING_TRANSACTION = new ProgressTracker.Step("Signing transaction with our private key.");
        private final ProgressTracker.Step GATHERING_BUYER_SIG = new ProgressTracker.Step("Gathering the buyer's signature.") {
            @Override
            public ProgressTracker childProgressTracker() {
                return CollectSignaturesFlow.Companion.tracker();
            }
        };
        private final ProgressTracker.Step GATHERING_SHIPPER_SIG = new ProgressTracker.Step("Gathering the shipper's signature.") {
            @Override
            public ProgressTracker childProgressTracker() {
                return CollectSignaturesFlow.Companion.tracker();
            }
        };
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
                GATHERING_BUYER_SIG,
                GATHERING_SHIPPER_SIG,
                FINALISING_TRANSACTION
        );

        public Initiator(Order order, Party buyer, Party seller) {
            order.setStatus(4);
            this.order = order;
            this.buyer = buyer;
            this.seller = seller;
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
        public UniqueIdentifier call() throws FlowException {
            // Obtain a reference to the notary we want to use.
            final Party notary = getServiceHub().getNetworkMapCache().getNotaryIdentities().get(0);

            // Stage 1.
            progressTracker.setCurrentStep(GENERATING_TRANSACTION);
            // Generate an unsigned transaction.
            Party me = getOurIdentity();

            UniqueIdentifier txId = new UniqueIdentifier();


            OrderState orderState = new OrderState(order, buyer, seller, me, txId);

            final Command<OrderContract.Commands.Delivery> txCommand = new Command<>(
                    new OrderContract.Commands.Delivery(),
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

            // Find other participants.
            List<AbstractParty> otherParticipants = orderState.getParticipants().stream()
                    .filter(party -> party == buyer || party == seller)
                    .collect(Collectors.toList());

            // Stage 4.
            progressTracker.setCurrentStep(FINALISING_TRANSACTION);
            Set<FlowSession> sessions = otherParticipants.stream().map(it ->
                    initiateFlow(it)).collect(Collectors.toSet());

            final SignedTransaction fullySignedTx = subFlow(
                    new CollectSignaturesFlow(signedTx, sessions, CollectSignaturesFlow.Companion.tracker()));

            subFlow(new FinalityFlow(fullySignedTx, sessions));

            return  txId;
        }
    }

    @InitiatedBy(com.gmedchain.flow.DeliveryOrderFlow.Initiator.class)
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