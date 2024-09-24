package io.xprnetwork.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.antelope.antelopejavaabieosserializationprovider.AbiEosSerializationProviderImpl;
import io.antelope.javarpcprovider.error.EosioJavaRpcProviderCallError;
import io.antelope.javarpcprovider.implementations.EosioJavaRpcProviderImpl;
import io.antelope.javasdk.error.rpcProvider.SendTransactionRpcError;
import io.antelope.javasdk.error.session.TransactionSendTransactionError;
import io.antelope.javasdk.error.session.TransactionSignAndBroadCastError;
import io.antelope.javasdk.implementations.ABIProviderImpl;
import io.antelope.javasdk.interfaces.IABIProvider;
import io.antelope.javasdk.interfaces.IRPCProvider;
import io.antelope.javasdk.interfaces.ISerializationProvider;
import io.antelope.javasdk.models.rpcProvider.Action;
import io.antelope.javasdk.models.rpcProvider.Authorization;
import io.antelope.javasdk.models.rpcProvider.TransactionConfig;
import io.antelope.javasdk.models.rpcProvider.response.RPCResponseError;
import io.antelope.javasdk.models.rpcProvider.response.SendTransactionResponse;
import io.antelope.javasdk.session.TransactionProcessor;
import io.antelope.javasdk.session.TransactionSession;
import io.antelope.softkeysignatureprovider.SoftKeySignatureProviderImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;


@RestController
@Component
@RequiredArgsConstructor
public class XPRNSDKController {

    @Autowired
    ObjectMapper objectMapper;

    @Value("${xprnetwork.private-key}") private String propsPrivateKey;
    @Value("${xprnetwork.permission}") private String propsPermission;
    @Value("${xprnetwork.from}") private String propsFrom;
    @Value("${xprnetwork.to}") private String propsTo;
    @Value("${xprnetwork.memo}") private String propsMemo;

    @GetMapping("health")
    public void health() {
        System.out.println("200 OK");
    }

    @GetMapping("/transaction")
    public String testTransaction() throws Exception {

        IRPCProvider rpcProvider = new EosioJavaRpcProviderImpl("http://api.protonnz.com:8888/");
        ISerializationProvider serializationProvider = new AbiEosSerializationProviderImpl();
        IABIProvider abiProvider = new ABIProviderImpl(rpcProvider, serializationProvider);
        SoftKeySignatureProviderImpl signatureProvider = new SoftKeySignatureProviderImpl();

        String privateKey = propsPrivateKey;
            signatureProvider.importKey(privateKey);


        TransactionSession session = new TransactionSession(
                serializationProvider,
                rpcProvider,
                abiProvider,
                signatureProvider
        );

        TransactionProcessor processor = session.getTransactionProcessor();

        // Now the TransactionConfig can be altered, if desired
        TransactionConfig transactionConfig = processor.getTransactionConfig();

        // Use blocksBehind (default 3) the current head block to calculate TAPOS
        transactionConfig.setUseLastIrreversible(false);
        // Set the expiration time of transactions 600 seconds later than the timestamp
        // of the block used to calculate TAPOS
        transactionConfig.setExpiresSeconds(600);

        // Update the TransactionProcessor with the config changes
        processor.setTransactionConfig(transactionConfig);

        String jsonData = "{\n" +
                "\"from\": \""+propsFrom+"\",\n" +
                "\"to\": \""+propsTo+"\",\n" +
                "\"quantity\": \"0.0001 XPR\",\n" +
                "\"memo\" : \""+propsMemo+"\"\n" +
                "}";

        List<Authorization> authorizations = new ArrayList<>();
        authorizations.add(new Authorization(propsFrom, propsPermission));
        List<Action> actions = new ArrayList<>();
        actions.add(new Action("eosio.token", "transfer", authorizations, jsonData));

        processor.prepare(actions);

        try {
            SendTransactionResponse sendTransactionResponse = processor.signAndBroadcast();
            ArrayList<Object> actionReturnValues = sendTransactionResponse.getActionValues();
        } catch (TransactionSignAndBroadCastError error) {
            //errors are wrapped at this point, we need to dig for specific causes (only for demo)
            if (error.getCause() instanceof TransactionSendTransactionError) {
                TransactionSendTransactionError sendTransactionError = (TransactionSendTransactionError) error.getCause();
                if (sendTransactionError.getCause() instanceof SendTransactionRpcError) {
                    SendTransactionRpcError sendTransactionRpcError = (SendTransactionRpcError) sendTransactionError.getCause();
                    if (sendTransactionRpcError.getCause() instanceof EosioJavaRpcProviderCallError) {
                        EosioJavaRpcProviderCallError eosioJavaRpcProviderCallError = (EosioJavaRpcProviderCallError) sendTransactionRpcError.getCause();
                        RPCResponseError rpcResponseError = eosioJavaRpcProviderCallError.getRpcResponseError();
                        return objectMapper.writeValueAsString(rpcResponseError);
                    }
                }
            }

            //root cause not solved above, just throw back the error (only for demo)
            throw error;
        }

        return "Transaction Completed";
    }

}
