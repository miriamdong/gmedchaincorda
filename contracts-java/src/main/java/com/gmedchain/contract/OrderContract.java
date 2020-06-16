package com.gmedchain.contract;

import com.example.state.IOUState;
import com.gmedchain.state.OrderState;
import com.sun.istack.NotNull;
import net.corda.core.contracts.CommandData;
import net.corda.core.contracts.CommandWithParties;
import net.corda.core.contracts.Contract;
import net.corda.core.contracts.ContractState;
import net.corda.core.identity.AbstractParty;
import net.corda.core.transactions.LedgerTransaction;

import java.util.List;
import java.util.stream.Collectors;

import static net.corda.core.contracts.ContractsDSL.requireSingleCommand;
import static net.corda.core.contracts.ContractsDSL.requireThat;

/**
 * A implementation of a Order smart contract.
 *
 * This contract enforces rules regarding the creation of a valid [IOUState], which in turn encapsulates an [IOU].
 *
 * For a new [Order] to be issued onto the seller and shipper, a transaction is required which takes:
 * - Zero input states.
 * - One output state: the new [Order].
 *
 * All contracts must sub-class the [Contract] interface.
 */
public class OrderContract implements Contract {
    public static final String ID = "com.gmedchain.contract.OrderContract";

    /**
     * The verify() function of all the states' contracts must not throw an exception for a transaction to be
     * considered valid.
     */
    @Override
    public void verify(@NotNull LedgerTransaction tx) throws IllegalArgumentException {
        List<ContractState> inputs = tx.getInputStates();
        List<ContractState> outputs = tx.getOutputStates();
        List<CommandWithParties<CommandData>> commands = tx.getCommands();
        CommandWithParties<Commands> command = requireSingleCommand(commands, Commands.class);

        if (command.getValue() instanceof Commands.Create) {
            requireThat(require -> {
                // Generic constraints around the Order transaction.
                require.using("No inputs should be consumed when creating an Create Order.", inputs.size() > 0);
                require.using("Only one output state should be created.", outputs.size() == 1);

                final OrderState outState = (OrderState) outputs.get(0);
                require.using("The buyer, the seller and the shipper cannot be the same entity.",
                        !outState.getBuyer().equals(outState.getSeller()) &&
                             !outState.getBuyer().equals(outState.getShipper()));
                require.using("Create Order contains at least 2 participants.", outState.getParticipants().size() > 2);

                // Order-specific state level constraints.
                require.using("The product SKU must be provided.", !outState.getProductSKU().isEmpty());
                require.using("The product name must be provided.", !outState.getProductName().isEmpty());
                require.using("The product price must be non-negative.", outState.getProductPrice() > 0);
                require.using("The product quantity must non-negative.", outState.getProductQty() > 0);
                require.using("The buyer address must be provided.", !outState.getBuyerAddress().isEmpty());
                require.using("The seller address must be provided.", !outState.getSellerAddress().isEmpty());
                require.using("The shipment price must non-negative.", outState.getShipmentPrice() > 0);

                return null;
            });
        } else if (command.getValue() instanceof Commands.Confirm) {

        } else if (command.getValue() instanceof Commands.ConfirmPickup) {

        } else if (command.getValue() instanceof Commands.Ship) {

        } else if (command.getValue() instanceof Commands.Delivery) {

        }
    }

    /**
     * This contract only implements associated commands for OrderState.
     */
    public interface Commands extends CommandData {
        class Create implements Commands {}
        class Confirm implements Commands {}
        class ConfirmPickup implements Commands {}
        class Ship implements Commands {}
        class Delivery implements Commands {}
    }
}