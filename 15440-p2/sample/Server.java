/**
 * Name: Haoyang Yuan
 * @author haoyangy
 * Server.java for 15440 project2 
 * 
 * This is the server file to be called by proxy to 
 * implement storage work. It will handle remote multiple
 * proxy calls to communicate. And it will always keep the
 * newest version of file uploaded from proxy or send to it.
 */

import java.io.File;
import java.io.RandomAccessFile;
import java.rmi.*;
import java.rmi.registry.LocateRegistry;
import java.rmi.server.UnicastRemoteObject;
import java.util.concurrent.ConcurrentHashMap;

/*** Server.java ***/
public class Server extends UnicastRemoteObject implements ServerInterface {

	//default serialVersionUID for serialize class, not used here
	private static final long serialVersionUID = 1L;
	
	/*Server parameters*/
	static int port;
    static String rootdir;

	/*version map*/
    public static ConcurrentHashMap<String, Integer> versions;

	/*Constructor*/
	public Server () throws RemoteException{ 
		versions = new ConcurrentHashMap<String, Integer>();
	}
	
	
	/**
	 * get the version and length information of the file
	 * and return -1 if it is not existing in the server
	 * 
	 * @param path - the name of the file
	 * @return the ret[] with ret[0] version ret[1] length
	 */
	public long[] getVerLen(String path) throws RemoteException{
		long[] ret = new long[2];
		String fullpath = rootdir + path;
		
		/*get the version of the file*/
		try{
			File file = new File(fullpath);
			if(!file.exists()) {
				ret[0]= -1; 
			}else{
				if(!versions.containsKey(fullpath)){
					versions.put(fullpath, 1); 
				} 
				ret[0] = versions.get(fullpath);
			}
		}catch(Exception e) {
			e.printStackTrace();
			ret[0] = -1;
		}
		
		/*get the length of the file*/
		long length = -1;
		try{
			File file = new File(fullpath);
			if(!file.exists()) {
				ret[1] = length; 
			}
			else{
				length = file.length();
				ret[1] = length;
			}
		}catch(Exception e) {
			e.printStackTrace();
			System.err.println("Server : getlen return false");
			ret[1] = -1;
		}		
		return ret;		
	}


	/**
	 * remove the file from the server
	 * 
	 * @param path - the name of the file
	 * @return true if remove success, false if not
	 */
	public synchronized boolean rmFile(String path) throws RemoteException{
		String fullpath = rootdir+path;
		try{
			File file = new File(fullpath);

			/*check if the file exists on server*/
			if(file.exists()==false){
				return false;
			}
			/*remove the file*/
			file.delete();
			if(versions.containsKey(fullpath)==true){
				versions.remove(fullpath);
			}
		}catch(Exception e) {
			e.printStackTrace();
			return false;
		}
		return true;
	}
	
	/**
	 * download the file from the server
	 * 
	 * @param path - the name of the file
	 * @param start - the start position of the download process
	 * @param len - the length of the data need to download
	 * @return the data downloaded
	 */
	public byte[] getFile(String path, long start, int len) 
			throws RemoteException{
		String fullpath = rootdir+path;
		
		byte[] b = new byte[len];
        try {
        	/*get file information*/
        	File file = new File(fullpath);
        	RandomAccessFile raf = new RandomAccessFile(file,"rw"); 
        	
        	/*download file from start position*/
        	raf.seek(start);
            raf.read(b);
        	raf.close();
        	
        } catch (Exception e) {
            e.printStackTrace();
        }
        return b;
	}
	
	/**
	 * create a new file on the server
	 * 
	 * @param path - the name of the file
	 * @return true if the creation success and false if not
	 */
	public synchronized boolean createFile(String path) throws RemoteException {
		String fullpath = rootdir+path;
		boolean ret = false;

		try{
        	File file = new File(fullpath);
        	if(file.exists()==true){
        		return true;
        	}
        	/*create directory for the file*/
        	if(file.getParentFile()!=null && file.getParentFile().exists()
        			==false) {
				new File(file.getParent()).mkdirs();
			}
        	 file.createNewFile();
        	 if (versions.containsKey(fullpath)){
    			 versions.put(fullpath, versions.get(fullpath) + 1);
    	     } else{
    	    	 versions.put(fullpath, 1);
    	     }
        	 ret = true;
        } catch (Exception e){
            e.printStackTrace();
        	return ret;
        }
		return ret;
		
	}
	/**
	 * get the uploaded file from the proxy
	 * 
	 * @param path - the name of the file
	 * @param buffer - the data is going to receive
	 * @param start - the start position to store the data
	 * @return true if the receive success and false if not
	 */
	public synchronized boolean uploadFile
			(String path, byte[] buffer, long start) throws RemoteException{
		String fullpath = rootdir+path;
		boolean ret = false;
		
		try{
        	File file = new File(fullpath);
        	/*create directory for file */
        	if(file.getParentFile()!=null && file.getParentFile().exists()
        			==false) {
				new File(file.getParent()).mkdirs();
			}
        	
        	/*write in the file data*/
        	RandomAccessFile raf = new RandomAccessFile(file,"rw"); 
        	raf.seek(start);
			raf.write(buffer);
			raf.close();
			
        } catch (Exception e){
            e.printStackTrace();
        	return ret;
        }
		/*update the map informaton*/
		ret = true;
		 if (versions.containsKey(fullpath)){
			 versions.put(fullpath, versions.get(fullpath) + 1);
	     } else{
	    	 versions.put(fullpath, 1);
	     }
        return ret;
	} 
	
    public static void main(String [] args) {
	   if(args.length < 2) {
		   return;
	   }
	   
	   /*get and process the parameters*/
	   int port = Integer.parseInt(args[0]);
	   rootdir = args[1];
	   
	   if (rootdir.charAt(rootdir.length()-1) != '/') {
		   rootdir += '/';
       }
	   
	   try {
		   LocateRegistry.createRegistry(port);
	   } catch(RemoteException e) {
		   e.printStackTrace();
	   }
	   
	   /*set up the server*/
	   Server server = null;
	   try {
		   	server = new Server();
	   }catch (RemoteException e) {
		   	e.printStackTrace();
		   	return;
	   }
   
	   /*starting up the service*/
	   try {
			Naming.rebind
			(String.format("//127.0.0.1:%d/ServerService", port), server);
	   }
	   catch (Exception e){
		   e.printStackTrace();
	   }
   }
}