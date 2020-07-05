package com.gmedchain.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gmedchain.common.Order;
import com.gmedchain.flow.*;
import com.gmedchain.state.OrderState;
import com.gmedchain.flow.ConfirmPickupFlow;
import net.corda.client.jackson.JacksonSupport;
import net.corda.core.contracts.*;
import net.corda.core.identity.CordaX500Name;
import net.corda.core.identity.Party;
import net.corda.core.messaging.CordaRPCOps;
import net.corda.core.node.NodeInfo;
import net.corda.core.transactions.SignedTransaction;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.http.MediaType.TEXT_PLAIN_VALUE;

/**
 * Define your API endpoints here.
 */
@RestController
@RequestMapping("/api/gmedchain/") // The paths for HTTP requests are relative to this base path.
public class MainController {
    private static final Logger logger = LoggerFactory.getLogger(RestController.class);
    private final CordaRPCOps proxy;
    private final CordaX500Name me;

    public MainController(NodeRPCConnection rpc) {
        this.proxy = rpc.getProxy();
        this.me = proxy.nodeInfo().getLegalIdentities().get(0).getName();

    }

    /** Helpers for filtering the network map cache. */
    public String toDisplayString(X500Name name){
        return BCStyle.INSTANCE.toString(name);
    }

    private boolean isNotary(NodeInfo nodeInfo) {
        return !proxy.notaryIdentities()
                .stream().filter(el -> nodeInfo.isLegalIdentity(el))
                .collect(Collectors.toList()).isEmpty();
    }

    private boolean isMe(NodeInfo nodeInfo){
        return nodeInfo.getLegalIdentities().get(0).getName().equals(me);
    }

    private boolean isNetworkMap(NodeInfo nodeInfo){
        return nodeInfo.getLegalIdentities().get(0).getName().getOrganisation().equals("Network Map Service");
    }

    @Configuration
    class Plugin {
        @Bean
        public ObjectMapper registerModule() {
            return JacksonSupport.createNonRpcMapper();
        }
    }

    @GetMapping(value = "/status", produces = TEXT_PLAIN_VALUE)
    private String status() {
        return "200";
    }

    @GetMapping(value = "/servertime", produces = TEXT_PLAIN_VALUE)
    private String serverTime() {
        return (LocalDateTime.ofInstant(proxy.currentNodeTime(), ZoneId.of("UTC"))).toString();
    }

    @GetMapping(value = "/addresses", produces = TEXT_PLAIN_VALUE)
    private String addresses() {
        return proxy.nodeInfo().getAddresses().toString();
    }

    @GetMapping(value = "/identities", produces = TEXT_PLAIN_VALUE)
    private String identities() {
        return proxy.nodeInfo().getLegalIdentities().toString();
    }

    @GetMapping(value = "/platformversion", produces = TEXT_PLAIN_VALUE)
    private String platformVersion() {
        return Integer.toString(proxy.nodeInfo().getPlatformVersion());
    }

    @GetMapping(value = "/peers", produces = APPLICATION_JSON_VALUE)
    public HashMap<String, List<String>> getPeers() {
        HashMap<String, List<String>> myMap = new HashMap<>();

        // Find all nodes that are not notaries, ourself, or the network map.
        Stream<NodeInfo> filteredNodes = proxy.networkMapSnapshot().stream()
                .filter(el -> !isNotary(el) && !isMe(el) && !isNetworkMap(el));
        // Get their names as strings
        List<String> nodeNames = filteredNodes.map(el -> el.getLegalIdentities().get(0).getName().toString())
                .collect(Collectors.toList());

        myMap.put("peers", nodeNames);
        return myMap;
    }

    @GetMapping(value = "/notaries", produces = TEXT_PLAIN_VALUE)
    private String notaries() {
        return proxy.notaryIdentities().toString();
    }

    @GetMapping(value = "/flows", produces = TEXT_PLAIN_VALUE)
    private String flows() {
        return proxy.registeredFlows().toString();
    }

    @GetMapping(value = "/states", produces = TEXT_PLAIN_VALUE)
    private String states() {
        return proxy.vaultQuery(ContractState.class).getStates().toString();
    }

    @GetMapping(value = "/me",produces = APPLICATION_JSON_VALUE)
    private HashMap<String, String> whoami(){
        HashMap<String, String> myMap = new HashMap<>();
        myMap.put("me", me.toString());
        return myMap;
    }
    @GetMapping(value = "/orders",produces = APPLICATION_JSON_VALUE)
    public List<StateAndRef<OrderState>> getOrderStates() {
        // Filter by state type: OrderState.
        return proxy.vaultQuery(OrderState.class).getStates();
    }

    @PostMapping (value = "create-order" , produces =  TEXT_PLAIN_VALUE , headers =  "Content-Type=application/x-www-form-urlencoded" )
    public ResponseEntity<String> createOrder(HttpServletRequest request) throws IllegalArgumentException {
        String sku = String.valueOf(request.getParameter("sku"));
        String name = String.valueOf(request.getParameter("name"));
        float price = Float.valueOf(request.getParameter("price"));
        int qty = Integer.valueOf(request.getParameter("qty"));
        int status = Integer.valueOf(request.getParameter("status"));
        float shippingCost = Float.valueOf(request.getParameter("shippingCost"));
        String buyerAddress = String.valueOf(request.getParameter("buyerAddress"));
        String sellerAddress = String.valueOf(request.getParameter("sellerAddress"));

        if (sku.isEmpty()) {
            return ResponseEntity.badRequest().body("Query parameter 'sku' must be provided.\n");
        }
        if (name.isEmpty()) {
            return ResponseEntity.badRequest().body("Query parameter 'productName' must be provided.\n");
        }
        if (price <= 0 ) {
            return ResponseEntity.badRequest().body("Query parameter 'productPrice' must be non-negative.\n");
        }
        if (qty <= 0 ) {
            return ResponseEntity.badRequest().body("Query parameter 'qty' must be non-negative.\n");
        }
        if (shippingCost < 0) {
            return ResponseEntity.badRequest().body("Query parameter 'ShippingCost' must be provided.\n");
        }
        if (buyerAddress.isEmpty()) {
            return ResponseEntity.badRequest().body("Query parameter 'BuyerAddress' must be provided.\n");
        }
        if (sellerAddress.isEmpty()) {
            return ResponseEntity.badRequest().body("Query parameter 'sellerAddress' must be provided.\n");
        }

        String party = request.getParameter("partyName");
        // Get party objects for myself and the counterparty.

        CordaX500Name partyX500Name = CordaX500Name.parse(party);
        Party otherParty = proxy.wellKnownPartyFromX500Name(partyX500Name);

        String partyName2 = "O=PartyC,L=Paris,C=FR";
        CordaX500Name partyX500Name2 = CordaX500Name.parse(partyName2);
        Party otherParty2 = proxy.wellKnownPartyFromX500Name(partyX500Name2) ;

        Order order = new Order(sku, name, price, qty, shippingCost, status, buyerAddress, sellerAddress);

        // Create a new OrderState using the parameters given.
        try {
            // Start the CreateOrderFlow. We block and waits for the flow to return.
            UniqueIdentifier result = proxy.startTrackedFlowDynamic(CreateOrderFlow.Initiator.class, order, otherParty, otherParty2).getReturnValue().get();
            // Return the response.
            return ResponseEntity
                    .status(HttpStatus.CREATED)
                    .body("Transaction id "+ result.getId() +" committed to ledger.\n " + result.toString());
            // For the purposes of this demo app, we do not differentiate by exception type.
        } catch (Exception e) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(e.getMessage());
        }
    }

    @PostMapping (value = "confirm-order" , produces =  TEXT_PLAIN_VALUE , headers =  "Content-Type=application/x-www-form-urlencoded" )
    public ResponseEntity<String> confirmOrder(HttpServletRequest request) throws IllegalArgumentException {
        String linearId = String.valueOf(request.getParameter("linearId"));
        int status = Integer.valueOf(request.getParameter("status"));

        if (linearId == null || linearId.isEmpty()) {
            return ResponseEntity.badRequest().body("Query parameter 'linearId' must be provided.\n");
        }
        if (status != 1) {
            return ResponseEntity.badRequest().body("Query parameter 'status' must be equals 1.\n");
        }

        // Conform a OrderState using the parameters given.
        try {
            // Start the ConfirmOrderFlow. We block and waits for the flow to return.
            SignedTransaction result = proxy.startTrackedFlowDynamic(ConfirmOrderFlow.Initiator.class, linearId, status).getReturnValue().get();
            // Return the response.
            return ResponseEntity
                    .status(HttpStatus.CREATED)
                    .body("Transaction id "+ result.getId() +" committed to ledger.\n " + result.toString());
            // For the purposes of this demo app, we do not differentiate by exception type.
        } catch (Exception e) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(e.getMessage());
        }
    }

    @PostMapping (value = "confirm-pickup" , produces =  TEXT_PLAIN_VALUE , headers =  "Content-Type=application/x-www-form-urlencoded" )
    public ResponseEntity<String> confirmPickup(HttpServletRequest request) throws IllegalArgumentException {
        String linearId = String.valueOf(request.getParameter("linearId"));
        int status = Integer.valueOf(request.getParameter("status"));

        if (linearId == null || linearId.isEmpty()) {
            return ResponseEntity.badRequest().body("Query parameter 'linearId' must be provided.\n");
        }
        if (status != 2) {
            return ResponseEntity.badRequest().body("Query parameter 'status' must be equals 1 (ReadyForPickup).\n");
        }

        // Confirm Pickup using the parameters given.
        try {
            // Start the ConfirmPickupFlow. We block and waits for the flow to return.
            SignedTransaction result = proxy.startTrackedFlowDynamic(ConfirmPickupFlow.Initiator.class, linearId, status).getReturnValue().get();
            // Return the response.
            return ResponseEntity
                    .status(HttpStatus.CREATED)
                    .body("Transaction id "+ result.getId() +" committed to ledger.\n " + result.toString());
            // For the purposes of this demo app, we do not differentiate by exception type.
        } catch (Exception e) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(e.getMessage());
        }
    }

    @PostMapping (value = "ship-order" , produces =  TEXT_PLAIN_VALUE , headers =  "Content-Type=application/x-www-form-urlencoded" )
    public ResponseEntity<String> shipOrder(HttpServletRequest request) throws IllegalArgumentException {
        String linearId = String.valueOf(request.getParameter("linearId"));
        int status = Integer.valueOf(request.getParameter("status"));

        if (linearId == null || linearId.isEmpty()) {
            return ResponseEntity.badRequest().body("Query parameter 'linearId' must be provided.\n");
        }
        if (status != 3) {
            return ResponseEntity.badRequest().body("Query parameter 'status' must be equals 3(Shipped).\n");
        }

        // Ship Order using the parameters given.
        try {
            // Start the ConfirmPickupFlow. We block and waits for the flow to return.
            SignedTransaction result = proxy.startTrackedFlowDynamic(ShipOrderFlow.Initiator.class, linearId, status).getReturnValue().get();
            // Return the response.
            return ResponseEntity
                    .status(HttpStatus.CREATED)
                    .body("Transaction id "+ result.getId() +" committed to ledger.\n " + result.toString());
            // For the purposes of this demo app, we do not differentiate by exception type.
        } catch (Exception e) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(e.getMessage());
        }
    }

    @PostMapping (value = "delivery-order" , produces =  TEXT_PLAIN_VALUE , headers =  "Content-Type=application/x-www-form-urlencoded" )
    public ResponseEntity<String> deliveryOrder(HttpServletRequest request) throws IllegalArgumentException {
        String linearId = String.valueOf(request.getParameter("linearId"));
        int status = Integer.valueOf(request.getParameter("status"));

        if (linearId == null || linearId.isEmpty()) {
            return ResponseEntity.badRequest().body("Query parameter 'linearId' must be provided.\n");
        }
        if (status != 4) {
            return ResponseEntity.badRequest().body("Query parameter 'status' must be equals 4(Delivered).\n");
        }

        // Delivery Order using the parameters given.
        try {
            // Start the ConfirmPickupFlow. We block and waits for the flow to return.
            SignedTransaction result = proxy.startTrackedFlowDynamic(DeliveryOrderFlow.Initiator.class, linearId, status).getReturnValue().get();
            // Return the response.
            return ResponseEntity
                    .status(HttpStatus.CREATED)
                    .body("Transaction id "+ result.getId() +" committed to ledger.\n " + result.toString());
            // For the purposes of this demo app, we do not differentiate by exception type.
        } catch (Exception e) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(e.getMessage());
        }
    }

    @PostMapping (value = "confirm-delivery" , produces =  TEXT_PLAIN_VALUE , headers =  "Content-Type=application/x-www-form-urlencoded" )
    public ResponseEntity<String> confirmDelivery(HttpServletRequest request) throws IllegalArgumentException {
        String linearId = String.valueOf(request.getParameter("linearId"));
        int status = Integer.valueOf(request.getParameter("status"));

        if (linearId == null || linearId.isEmpty()) {
            return ResponseEntity.badRequest().body("Query parameter 'linearId' must be provided.\n");
        }
        if (status != 5) {
            return ResponseEntity.badRequest().body("Query parameter 'status' must be equals 5(ConfirmDelivery).\n");
        }

        // Delivery Order using the parameters given.
        try {
            // Start the ConfirmPickupFlow. We block and waits for the flow to return.
            SignedTransaction result = proxy.startTrackedFlowDynamic(ConfirmDeliveryFlow.Initiator.class, linearId, status).getReturnValue().get();
            // Return the response.
            return ResponseEntity
                    .status(HttpStatus.CREATED)
                    .body("Transaction id "+ result.getId() +" committed to ledger.\n " + result.toString());
            // For the purposes of this demo app, we do not differentiate by exception type.
        } catch (Exception e) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(e.getMessage());
        }
    }

    /**
     * Displays all OrderState that only this node has been involved in.
     */
    @GetMapping(value = "my-orders",produces = APPLICATION_JSON_VALUE)
    public ResponseEntity<List<StateAndRef<OrderState>>> getMyOrders() {
        List<StateAndRef<OrderState>> myorders = proxy.vaultQuery(OrderState.class).getStates().stream().filter(
                it -> it.getState().getData().getBuyer().equals(proxy.nodeInfo().getLegalIdentities().get(0))).collect(Collectors.toList());
        return ResponseEntity.ok(myorders);
    }
}