/**
 * Name: Haoyang Yuan
 * @author haoyangy
 * Server.java for 15440 project3 
 * 
 * This is the server file to simulate the instances of a scalable cloud
 * The instance can be treated as front server, middle server
 * and master server depending on the order and need. The original master
 * server will automatically invoke new servers according to clients
 * and this project also implements a simple cache to help deal with the
 * client read requests.
 *  
 */

import java.util.*;
import java.util.concurrent.*;
import java.rmi.*;
import java.rmi.server.*;


/*** Server.java ***/
public class Server extends UnicastRemoteObject implements ServerInterface {
	
	
	
	/* public members instances need to operate*/
	private static final long serialVersionUID = 1L;
	public static String ip;
	public static int port;
	public static Server self;
	public static Feature features = new Feature();
	public static ServerLib selfLib;
	
	/* public members master server need to operate*/

	/* record the instances*/
	public static ConcurrentHashMap<Integer,Boolean> VMs;
	public static LinkedBlockingQueue<Cloud.FrontEndOps.Request> MRs;
	public static int VMFirstnum = 0;
	public static int VMSecondnum = 0;
	public static boolean nextFirst =  false;
	public static Cloud.DatabaseOps cache;
	

	/* threshold to decide start or shutdown servers*/
	public static int dropcount = 0;
	public static int appdropcount = 0;
	public static boolean booting = true;
	public final static int firstoutpara = 4;
	public final static int initialOutRate = 6;

	/* public members front/middle server need to operate*/
	public static int secondshutcount = 0;
	public final static int secondshutthresh = 3;
	public final static int secondshuttime = 600;
	public static ServerInterface master;
	 
	/*Constructor*/
	protected Server() throws RemoteException {
		super();
	}
	

    /**
     * get the server instance according to their id from the RMI
     * 
     * @param ip - the cloud ip
     * @param port - the cloud port
     * @param id - the id of the instance
     * @return the interface of instance from the RMI
     */
	 public static ServerInterface getServer(String ip, int port,String id){
		 try{
			 ServerInterface reServer = (ServerInterface) Naming.lookup
				(String.format("//%s:%d/%s", ip, port, id));
			 return reServer;
		 }catch (Exception e){
			  System.err.println(e);
			  return null;
		 }
	 }
	 

    /**
     * Unregister/unbind a front or middle server when scale in
     * 
     * @param id - the id of the instance
     * @return true if unbind successful else return false
     */
	 public static boolean  UnregistNotMaster(String id){
		 try{
			 Naming.unbind(String.format("//%s:%d/%s", ip, port, id));
			 return true;
		 }catch (Exception e){
			  System.err.println(e);
			  return false;
		 }
	 }
	 

    /**
     * get the cache instance of the cloud
     * 
     * @param ip - the ip of the instance
     * @param port - the port of the cloud
     * @return the interface of cache from the RMI
     */
	 public static  Cloud.DatabaseOps getCache(String ip, int port){
		 try{
			 Cloud.DatabaseOps recache = (Cloud.DatabaseOps)Naming.lookup
					 (String.format("//%s:%d/%s", ip, port, "Ca"));
			 return recache;
		 }catch (Exception e){
			  System.err.println(e);
			  return null;
		 }
	 }
	 

    /**
     * register the cache
     * 
     * @param ip - the ip of the instance
     * @param port - the port of the cloud
     * @param db - the database cache interface to register
     * @return true if register successful, other wise false
     */
	 public static  boolean registIsCache(String ip, 
			 int port, Cloud.DatabaseOps db) throws RemoteException {
		try{
			cache = new Cache(db);	
			Naming.bind(String.
					format("//%s:%d/%s",ip,port,"Ca"), cache);		
			return true;
		}catch (Exception e){
			return false;
		} 
	 }
	
	 /**
     * register the master server to RMI
     * 
     * @param ip - the ip of the instance
     * @param port - the port of the cloud
     * @return true if register successful, other wise false
     */ 
	 public static  boolean registIsMaster(String ip, int port) 
			 throws RemoteException {
		try{
			Server selfServer = new Server();	
			VMs = new ConcurrentHashMap<Integer,Boolean>();
			MRs = new LinkedBlockingQueue<Cloud.FrontEndOps.Request>();
			Naming.bind(String.format
					("//%s:%d/%s", ip, port, "Ma"), selfServer);
			return true;
		}catch (Exception e){
			return false;
		} 
	 }
	 
	 /**
     * shutdown a server by its id
     * 
     * @param id - the server to shutdown
     */ 
	 public static void shutDown(int id){
		 try{
			 /*unregister if its a front server*/
			 selfLib.interruptGetNext();
			 if(features.isFirstLevel==true){
				 selfLib.unregister_frontend();
			 }
			 
			 /*delete server record from the master server*/
			 master.deleteServer(id);
			 //UnicastRemoteObject.unexportObject(self,true);
			 
			 /*ubind from RMI and endVM process*/
			 UnregistNotMaster(Integer.toString(id));
			 selfLib.shutDown();
			 selfLib.endVM(id);
		 }catch (Exception e){
			 System.err.println(e);
		 }
	 }
	 

	 /**
     * register a front or middle server
     * 
     * @param ip - the ip of cloud
     * @param port - the port of cloud
     * @param id - the server to register
     * @return true if register successful, otherwise false
     */ 
	 public static boolean registNotMaster(String ip, int port,int id)
			 throws RemoteException {
		try{
			Server selfServer = new Server();		
			Naming.bind(String.format("//%s:%d/%d", ip, port, id), selfServer);
			return true;
		}catch (Exception e){
			return false;
		} 
	 }
	 

	 /**
     * delete a server from record of master server
     * 
     * @param id - the server to register
     * @return true if delete successful, otherwise false
     */ 
	 public boolean deleteServer(int id){
		 /* delete from VM record map*/
		 if(VMs.containsKey(id)==false){
			 return false;
		 }
		 boolean isFirst = VMs.get(id);
		 VMs.remove(id);
		 
		 /*reduce VM count*/
		 if(isFirst) {
			 VMFirstnum--;
		 }
		 else {
			 VMSecondnum--;
		 }
		 System.out.println("scale in node "+id);
		 return true;
	 }
	 
	 /**
     * add request to the master queue
     * 
     * @param r - the request from the front server
     */ 
	 public  void addRequest(Cloud.FrontEndOps.Request r){
		 MRs.add(r);
	 }
	 
	 /**
     * poll a request from the master queue
     * 
     * @return the request if success, null if timeout
     */
	 public  Cloud.FrontEndOps.Request pollRequest(){
		try {
			/* wait until maximum timeout limit*/
			return MRs.poll(secondshuttime,TimeUnit.MILLISECONDS);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		return null;
	 }
	 
	 /**
     * return the size of the master queue
     * 
     * @return the request queue length
     */
	 public  int requestSize(){
		 return MRs.size();
	 }

	 /**
     * get the number of the front/middle server
     * 
     * @param isFirst - get the front server or middle server
     * @return the number of the server
     */
	 public int getServerNum(boolean isFirst){
		 if(isFirst) return VMFirstnum;
			return VMSecondnum;
	 }
	 
	 /**
     * notify the server if a drop happened
     * 
     * @param idDrop - if a request has been dropped according
     */
	 public synchronized void notifydrop(boolean isDrop){
		 if(isDrop){
			 appdropcount++;
			 /* start up a new middle server if two drops happened */
			 if(appdropcount==2){
				 System.out.println("Process Scale one middle ");
				 scaleout(false);
				 appdropcount = 0;
			 }
		 }else{
			 appdropcount = 0;
		 }
	 }
	 
	 /**
     * to check if the instance is a front or middle server
     * 
     * @param id - the id of the server
     * @return true if the server is a front server, false if a middle
     */
	 public boolean isNextFirst(int id){
		 return VMs.get(id);
	 }
	 
	 
	 /**
     * master can add a request to its requests queue
     * 
     * @param r - the request to be added to the queue
     */
	 public static  void addRequestSelf(Cloud.FrontEndOps.Request r){
		 MRs.add(r);
	 }
	
	 /**
     * master record a new booting server
     * 
     * @param id - the id of the server instance
     * @param isFirst - if the server is a front server or middle server
     * @return the server id if successful, -1 if fails
     */
	 public static int addVM(int id,boolean isFirst){
		 if(VMs.containsKey(id)==false){
			 VMs.put(id, isFirst);
			 return id;
		 }
		 return -1;
	 }
	 
	 /**
     * master server invokes to start a new server
     * 
     * @param isFront - if the new server needed is a front server
     */
	 public static synchronized void scaleout(boolean isFront){
		 	/* start a new server */
		 	nextFirst =  isFront;
			int sonid = selfLib.startVM();
			/* record/count the server to master */
			addVM(sonid,isFront);
			countVM();
	 }
	 
	 /**
     * count the number of current servers
     */
	 public synchronized static void countVM(){
		 /* count the server according to global member boolean */
		 if(nextFirst) VMFirstnum++;
		 else VMSecondnum++;
	 }
	
	 /**
     * main method to be invoked originally
     */
	 public static void main ( String args[] ) throws Exception {
		
		if (args.length < 2) throw new 
			Exception("Need >= 2 args: <cloud_ip> <cloud_port> [<vm_id>]");
		
		/* set up the global parameters */
		selfLib = new ServerLib( args[0], Integer.parseInt(args[1]) );
		ip = args[0];
		port = Integer.parseInt(args[1]);
		features.id = Integer.parseInt(args[2]);
				
		/* reigister master and create front/middle server */
		if(registIsMaster(ip,port)==true){
			
			/* register cache */
			Cloud.DatabaseOps db = selfLib.getDB();
			registIsCache(ip,port,db);
	    	
			/* register master */
			features.isMaster = true;				
			selfLib.register_frontend();
			addVM(1,true);
			VMFirstnum++;
			
			/* master start one front/middle server originally*/
			System.out.println("Initial Scale one front");
			System.out.println("Initial Scale one middle");
			scaleout(false);
			scaleout(true);
		}
		/* this is a front/mid server*/
		else{
			
			/*get type information from master and get cache*/
			features.isMaster = false;
			master = getServer(ip,port,"Ma");
			features.isFirstLevel = master.isNextFirst(features.id);
			registNotMaster(ip,port,features.id);
			if(features.isFirstLevel) selfLib.register_frontend();
			cache = getCache(ip,port);
		}

		/* main working loop */
		while (true) {
		try{
			/* master operation */
			if(features.isMaster==true){
				/* booting time, need to count the original spped */			
				if( booting ){
					if(selfLib.getStatusVM(2) 
							!= Cloud.CloudOps.VMStatus.Running) {
						
						/* count the original spped*/
						if(selfLib.getQueueLength()>0) {
							selfLib.dropHead();
							dropcount++;
							
							/* start server according to speed*/
							if(dropcount%initialOutRate==0){
								System.out.
								println("Initial Scale one middle ");
								scaleout(false);
							}
						}
						continue;
					}
					
					/* first middle server started booting finish */			
					booting = false;
				}else {
					/* working as a front */				
					Cloud.FrontEndOps.Request r = selfLib.getNextRequest();
					addRequestSelf(r);
				}
			
				/* parameters need  */
				int firstNeed = selfLib.getQueueLength();					
				if(firstNeed > firstoutpara*(VMFirstnum) ){
					scaleout(true);
				}
			}
			/* not master, is front server */
			else if(features.isFirstLevel==true){

				Cloud.FrontEndOps.Request r = selfLib.getNextRequest();
				master.addRequest(r);					
				
			/* not master, is middle server */
			}else{
				/* shutdown if idle more than threshold */
				int sec = master.getServerNum(false);
				if(secondshutcount >= secondshutthresh && sec>2 ) {
					shutDown(features.id);
				}
				
				int len = master.requestSize();
				Cloud.FrontEndOps.Request r = master.pollRequest();
					
				if(r!=null){
					
					/* drop request if long length and notify server*/
					if( len > sec){
						selfLib.drop(r);						
						master.notifydrop(true);
					}else{
						selfLib.processRequest(r,cache);							
						master.notifydrop(false);
					}
					secondshutcount = 0;
					
					/* count shutdown threshold */
				}else{
					secondshutcount++;
					}
				}				
		}catch (Exception e){	}
		}
	}
}

