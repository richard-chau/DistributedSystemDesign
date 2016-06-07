/**
 * Name: Haoyang Yuan
 * @author haoyangy
 * Cache.java for 15440 project3 
 * 
 * cache for server to communicate with database 
 */

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/*** Cache.java ***/
public class Cache extends UnicastRemoteObject 
		implements Cloud.DatabaseOps, java.io.Serializable {

	/* cache members */
	private Map<String, String> items;
	private Cloud.DatabaseOps db;
	
	/* constructor */
	protected Cache(Cloud.DatabaseOps originaldb) 
			throws RemoteException {
		items = new ConcurrentHashMap<String,String>();
		db = originaldb;
		
	}
	
	 /**
     * get item information from database
     * 
     * @param arg0 - the id of the server instance
     * @return item information or null is not exist
     */
	@Override
	public String get(String arg0) throws RemoteException {
		// TODO Auto-generated method stub
		if(items.containsKey(arg0)==false){
			String ret = db.get(arg0);
			items.put(arg0, ret);
			return ret;
		}

		/* all get information are processed locally */
		return items.get(arg0);
	}
	
	/**
     * set item information to database
     * 
     * @param arg0 - the item name
     * @param arg1 - the item price
     * @param arg2 - the item quantity
     * @return true if set successful, false if not
     */
	@Override
	public boolean set(String arg0, String arg1, String arg2) 
			throws RemoteException {

		/* all set operations are passed by */
		return db.set(arg0, arg1, arg2);
	}

	/**
     * transaction for purchase operation
     * 
     * @param arg0 - the item name
     * @param arg1 - the item price
     * @param arg2 - the item quantity
     * @return true if set successful, false if not
     */
	@Override
	public boolean transaction(String arg0, float arg1, int arg2) 
			throws RemoteException {

		/* all transact operations are passed by */
		return  db.transaction(arg0, arg1, arg2);
	}
}