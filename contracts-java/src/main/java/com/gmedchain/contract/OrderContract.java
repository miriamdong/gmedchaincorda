package com.gmedchain.contract;

import com.gmedchain.state.OrderState;
import com.gmedchain.common.Types;
import com.sun.istack.NotNull;
import net.corda.core.contracts.CommandData;
import net.corda.core.contracts.CommandWithParties;
import net.corda.core.contracts.Contract;
import net.corda.core.contracts.ContractState;
import net.corda.core.transactions.LedgerTransaction;

import java.util.List;

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
        final OrderState outState = (OrderState) outputs.get(0);

        // Generic constraints around the Order transaction.
        requireThat(require -> {
            require.using("Only one output state should be produced.", outputs.size() == 1);

            require.using("The buyer, the seller and the shipper cannot be the same entity.",
                    !outState.getBuyer().equals(outState.getSeller()) &&
                            !outState.getBuyer().equals(outState.getShipper()));
            require.using("Order contains at least 3 participants.", outState.getParticipants().size() > 2 );

            // Order-specific state level constraints.
            require.using("The product SKU must be provided.", !outState.getOrder().getProductSku().isEmpty());
            require.using("The product name must be provided.", !outState.getOrder().getProductName().isEmpty());
            require.using("The product price must be non-negative.", outState.getOrder().getProductPrice() > 0);
            require.using("The product quantity must non-negative.", outState.getOrder().getQty() > 0);
            require.using("The buyer address must be provided.", !outState.getOrder().getBuyerAddress().isEmpty());
            require.using("The seller address must be provided.", !outState.getOrder().getSellerAddress().isEmpty());
            require.using("The shipment price must non-negative.", outState.getOrder().getShippingCost() > 0);

            return null;
        });

        if (command.getValue() instanceof Commands.Create) {
            requireThat(require -> {
                require.using("No inputs should be consumed when creating an order.", inputs.size() == 0);
                require.using("The order status value must be 0(Ordered) for create order.", outState.getOrder().getStatus() == 0);
                return null;
            });
        } else if (command.getValue() instanceof Commands.Confirm) {
            System.out.println("inputs.size()" + inputs.size());
            requireThat(require -> {
               require.using("Only one input should be consumed when confirming an order.", inputs.size() == 1);
               require.using("The order status value must be 1(Confirmed) for confirm order", outState.getOrder().getStatus() == 1);
               return null;
            });
        } else if (command.getValue() instanceof Commands.ConfirmPickup) {
            requireThat(require -> {
                require.using("Only one input should be consumed when confirming pickup an order.", inputs.size() == 1);
                require.using("The order status value must be 2(ConfirmPickup) for ConfirmPickup Order", outState.getOrder().getStatus() == 2);
                return null;
            });
        } else if (command.getValue() instanceof Commands.Ship) {
            requireThat(require -> {
                require.using("Only one input should be consumed when shipping pickup an order.", inputs.size() == 1);
                require.using("The order status value must be 3(Shipped) for shipping an order", outState.getOrder().getStatus() == 3);
                return null;
            });
        } else if (command.getValue() instanceof Commands.Delivery) {
             requireThat(require -> {
                require.using("Only one input should be consumed when delivering an order.", inputs.size() == 1);
                require.using("The order status value must be 4(Delivered) for delivering an order.", outState.getOrder().getStatus() == 4);
                return null;
            });
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