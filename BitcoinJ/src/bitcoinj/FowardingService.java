/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package bitcoinj;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.LegacyAddress;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.kits.WalletAppKit;
import org.bitcoinj.params.TestNet3Params;
import org.bitcoinj.wallet.Wallet;

import java.io.File;
import org.bitcoinj.core.BlockChain;
import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.core.PeerGroup;
import org.bitcoinj.store.BlockStore;
import org.bitcoinj.store.MemoryBlockStore;
import org.bitcoinj.wallet.Wallet.BalanceType;

/**
 *
 * @author syazwani.s
 */
public class FowardingService {
    
    private static Address forwardingAddress;
    private static WalletAppKit kit;

    public static void main(String[] args) throws Exception {
        
        
        // Figure out which network we should connect to. Each one gets its own set of files.        
        final NetworkParameters params = TestNet3Params.get();
                
        // Parse the address given as the first parameter.
        forwardingAddress = LegacyAddress.fromBase58(params, "mtCjxRYXhTDN6YhknvYaRdCeUfdFyvTcGh");

        System.out.println("Network: " + params.getId());
        System.out.println("Forwarding address: " + forwardingAddress);

        File walletFile = new File("myWallet.wallet");
        final Wallet myWallet = Wallet.loadFromFile(walletFile);
        
        BlockStore blockStore = new MemoryBlockStore(params);
        BlockChain chain = new BlockChain(params, myWallet, blockStore);
        
        PeerGroup peerGroup = new PeerGroup(params, chain);
        peerGroup.addWallet(myWallet);
        peerGroup.startAsync();


        WalletAppKit kit = new WalletAppKit(params, new File("."), "myWallet"){
            @Override
            protected void onSetupCompleted(){
                if(wallet().getKeyChainGroupSize()<1){ // check that the wallet has at least one key  
                    wallet().importKey(myWallet.currentReceiveKey().decompress());
                }
            }
            
        };
        
        kit.startAsync();
        kit.awaitRunning();
                
        Coin value = Coin.parseCoin("0.001");
        // Get the address 1RbxbA1yP2Lebauuef3cBiBho853f7jxs in object form.
        Address targetAddress = LegacyAddress.fromBase58(params, "1RbxbA1yP2Lebauuef3cBiBho853f7jxs");
        
        System.out.println("Send money to: " + targetAddress.toString());
        
        try {
            Wallet.SendResult result = kit.wallet().sendCoins(kit.peerGroup(), targetAddress, value);
            System.out.println("coins sent. transaction hash: " + result.tx.getTxId());
            // Save the wallet to disk, optional if using auto saving (see below).
            myWallet.saveToFile(walletFile);
            // Wait for the transaction to propagate across the P2P network, indicating acceptance.
            result.broadcastComplete.get();
        } catch (InsufficientMoneyException e) {
            System.out.println("Not enough coins in your wallet. Missing " + e.missing.getValue() + " satoshis are missing (including fees)");
            System.out.println("Send money to: " + kit.wallet().currentReceiveAddress().toString());
        } 
       
        
        ListenableFuture<Coin> balanceFuture = kit.wallet().getBalanceFuture(value, BalanceType.AVAILABLE);
            FutureCallback<Coin> callback = new FutureCallback<Coin>() {
                @Override
                public void onSuccess(Coin balance) {
                    System.out.println("coins arrived and the wallet now has enough balance");
                }

                @Override
                public void onFailure(Throwable t) {
                    System.out.println("something went wrong");
                }
            };
            Futures.addCallback(balanceFuture, callback, MoreExecutors.directExecutor());
        }

    }

    
