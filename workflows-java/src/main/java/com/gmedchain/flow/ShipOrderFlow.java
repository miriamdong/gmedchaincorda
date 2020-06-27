package com.gmedchain.flow;

import co.paralleluniverse.fibers.Suspendable;
import com.gmedchain.common.Order;
import com.gmedchain.utils.FlowUtils;
import com.google.common.collect.ImmutableSet;
import net.corda.core.contracts.*;
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
public class ShipOrderFlow {

    @InitiatingFlow
    @StartableByRPC
    public static class Initiator extends FlowLogic<SignedTransaction> {
        private final UniqueIdentifier linearId;
        private final int orderStatus;

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


        public Initiator(UniqueIdentifier linearId, int orderStatus) {
            this.linearId = linearId;
            this.orderStatus = orderStatus;
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

            StateAndRef<OrderState> stateAndRef = null;
            try {
                stateAndRef = FlowUtils.retrieveOrderState(linearId, getServiceHub().getVaultService());
            } catch (NoSuchFieldException e) {
                e.printStackTrace();
            }

            TransactionState transactionState = stateAndRef.getState();
            OrderState orderState = (OrderState) transactionState.getData();

            orderState.getOrder().setStatus(orderStatus);
            orderState.setOwner(me);
            final Command<OrderContract.Commands.Ship> txCommand = new Command<>(
                    new OrderContract.Commands.Ship(),
                    me.getOwningKey());
            final TransactionBuilder txBuilder = new TransactionBuilder(notary)
                    .addInputState(stateAndRef)
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
                    .filter(party -> party == orderState.getBuyer() || party == orderState.getSeller())
                    .collect(Collectors.toList());

            // Stage 4.
            progressTracker.setCurrentStep(FINALISING_TRANSACTION);
            Set<FlowSession> sessions = otherParticipants.stream().map(it ->
                    initiateFlow(it)).collect(Collectors.toSet());

            final SignedTransaction fullySignedTx = subFlow(
                    new CollectSignaturesFlow(signedTx, sessions, CollectSignaturesFlow.Companion.tracker()));



            return  subFlow(new FinalityFlow(fullySignedTx, sessions));
        }
    }

    @InitiatedBy(com.gmedchain.flow.ShipOrderFlow.Initiator.class)
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