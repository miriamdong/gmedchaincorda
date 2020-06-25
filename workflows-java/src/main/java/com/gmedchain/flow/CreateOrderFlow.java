package com.gmedchain.flow;

import com.gmedchain.common.Order;
import com.gmedchain.state.OrderState;
import com.gmedchain.contract.OrderContract;
import co.paralleluniverse.fibers.Suspendable;
import com.google.common.collect.ImmutableSet;
import net.corda.core.contracts.Command;
import net.corda.core.contracts.UniqueIdentifier;
import net.corda.core.flows.*;
import net.corda.core.identity.AbstractParty;
import net.corda.core.identity.Party;
import net.corda.core.transactions.SignedTransaction;
import net.corda.core.transactions.TransactionBuilder;
import net.corda.core.utilities.ProgressTracker;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class CreateOrderFlow {

    @InitiatingFlow
    @StartableByRPC
    public static class Initiator extends FlowLogic<UniqueIdentifier> {
        private final Order order;
        private final Party seller;
        private final Party shipper;

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

        public Initiator(Order order, Party seller, Party shipper) {
          this.order = order;
          this.seller = seller;
          this.shipper = shipper;
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

            UniqueIdentifier linearId = new UniqueIdentifier();
            OrderState orderState = new OrderState(order, me, seller, shipper, linearId);

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

            // Find other participants.
            List<AbstractParty> otherParticipants = orderState.getParticipants().stream()
                    .filter(party -> party == seller || party == shipper)
                    .collect(Collectors.toList());

            // Stage 4.
            progressTracker.setCurrentStep(FINALISING_TRANSACTION);
            Set<FlowSession> sessions = otherParticipants.stream().map(it ->
                    initiateFlow(it)).collect(Collectors.toSet());

            subFlow(new FinalityFlow(signedTx, sessions));
            return linearId;
        }
    }

    @InitiatedBy(com.gmedchain.flow.CreateOrderFlow.Initiator.class)
    public static class Acceptor extends FlowLogic<SignedTransaction> {

        private final FlowSession otherPartySession;

        public Acceptor(FlowSession otherPartySession) { this.otherPartySession = otherPartySession; }

        @Suspendable
        @Override
        public SignedTransaction call() throws FlowException {
            return subFlow(new ReceiveFinalityFlow(otherPartySession));
        }
    }
}