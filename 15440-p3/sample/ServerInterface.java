/**
 * Name: Haoyang Yuan
 * @author haoyangy
 * ServerInterface.java for 15440 project3 
 * 
 * ServerInterface to help with RMI opeartion
 *  
 */

import java.util.*;
import java.util.concurrent.*;
import java.rmi.*;

/*** ServerInterface.java ***/
public interface ServerInterface extends Remote {    
    
	/* members for server */
    ServerLib  selfLib = null;
    Feature features = null;
    boolean nextFirst = false;
	int VMSecondnum = 0;
	ConcurrentHashMap<Integer,Boolean> VMs = null;

	/* interfaces the master will use */	
	boolean isNextFirst(int id) throws RemoteException;
	void addRequest(Cloud.FrontEndOps.Request r) throws RemoteException;
	Cloud.FrontEndOps.Request pollRequest() throws RemoteException;
  	int requestSize() throws RemoteException;
	int getServerNum(boolean b) throws RemoteException;
	boolean deleteServer(int id) throws RemoteException;
	void notifydrop(boolean b) throws RemoteException;
}