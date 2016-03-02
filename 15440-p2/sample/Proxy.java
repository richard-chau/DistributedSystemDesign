/**
 * Name: Haoyang Yuan
 * @author haoyangy
 * Proxy.java for 15440 project2 
 * 
 * This is the proxy file to be called by client to 
 * implement proxy work. It will handle remote multiple
 * client calls to communicate with the server. And 
 * it will LRU cache the files locally to provide efficient
 * file visit
 */

/*The header files*/
import java.io.*;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.rmi.Naming;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.util.Arrays;
import java.util.Stack;
import java.util.concurrent.ConcurrentHashMap;
import java.nio.file.*;

/*** Proxy.java ***/
class Proxy {
	/*Remote communication parameters*/
	private static Object lock = new Object();
	private static String serverip;
	private static int serverport;
	private static String proxydir;
	private static long cachesize;
	public static int BLOCK = 2000000;
	
	/*Server instance from Server.java*/
	private static ServerInterface server;
	/*Cache instance from LRUCache.java*/
	private static LRUCache cache;

	/*Map to record file in use and file version*/
	public static ConcurrentHashMap<String, Integer> open_file 
							= new ConcurrentHashMap();
	public static ConcurrentHashMap<String, Integer> late_file 
							= new ConcurrentHashMap();
	
	/**
	 * delete to file locally, invoked by the cache clean
	 * 
	 * @param path - the file name that should be deleted
	 * @return true if the file is deleted, false if not
	 */
	public static  boolean cachedelete( String path ) {
		String fullpath = proxydir + path;
		try {

			File file = new File(fullpath);
			if(file.isDirectory()) {
				return false;
			}
			if(open_file.containsKey(path)){
				return false;
			}
			
			if(file.exists()==true) {
				file.delete();				
			}
			/* return true if file no longer in proxy*/
			return true;
		}
		catch(Exception e) {
			e.printStackTrace();
		}
		return false;
	}
	
	/**
	 * clean the cache to make space of given size, and 
	 * update the file, delete all unused old version in cache
	 * 
	 * @param size - the free size is need in the cache
	 * @param oldpath - the path should be inserted, delete old version
	 * @return true if the file is deleted, false if not
	 */
	public static boolean cachemakespace( long size, String oldpath ) {
		try {
			LRUCache.Node node = null;
			long remain = cache.getremain();
			String simpath = oldpath.split("-")[0];

			/* delete all the unused same name old version file*/
			while( (node = cache.getnext(node) )!=null){
				if(open_file.containsKey(node.path)){
					continue;
				}else if(node.path.split("-")[0].equals(simpath)){
					if(cachedelete(node.path)){
						cache.remove(node);	
					}
				}
			}
			
			/*check if the cache is large enough*/
			remain = cache.getremain();		
			if(remain >= size){
				System.err.println("cache space: good");
				return true;
			}
			
			/*iterate again to delete unused file*/
			node =null;
			while( (node = cache.getnext(node) )!=null){				
				if(open_file.containsKey(node.path)){
					continue;
				}else{
					if(cachedelete(node.path)){
						cache.remove(node);	
					}
				}
				
				/*break if the cache is large enough now*/
				remain = cache.getremain();				
				if(remain>=size){
					return true;
				}
			}
		}
		catch(Exception e) {
			e.printStackTrace();
		}
		return false;
	}
	
	/*** Proxy.java ***/
	private static class FileHandler implements FileHandling {
		
		/*open file descriptor and corresponding file & copy*/
		public static ConcurrentHashMap<Integer, MyFile> fd_file 
					= new ConcurrentHashMap<Integer, MyFile>();
		public static ConcurrentHashMap<Integer, MyFile> local_fd_file
					= new ConcurrentHashMap<Integer, MyFile>();
		
		/**
		 * download file from the server
		 * 
		 * @param path - the file need download
		 * @param filelen - server length
		 * @return true if download successes, false if not
		 */
		public boolean getFile(String path,long filelen){			
			
			 try {
				 	/*get the simplified path*/
				 	String simpath = path;
				 	if(path.contains("-")){
				 		 simpath = path.split("-")[0];
				 	}
				 	long len = filelen;
				 	
				 	/*if file is on the server*/
					if(len==-1){
						return false;
					}
					
				 	long cnt = 0;
				 	String fullpath = proxydir + path;
				 	RandomAccessFile raf = 
				 			new RandomAccessFile(fullpath, "rw");
				 	
				 	/*continuing call until all bytes are recieved*/
				 	while (cnt < len) {
	                    int block = (int) Math.min(BLOCK, len - cnt);
	                    byte[] b = server.getFile(simpath, cnt, block);
	                    raf.write(b);
	                    cnt += b.length;
	                }
	                raf.close();

	            } catch (Exception e) {
	                e.printStackTrace();
	                return false;
	            }
	            return true;
		}
		
		/**
		 * upload file from the server
		 * 
		 * @param fd - the fd of the local file need send
		 * @return true if upload successes, false if not
		 */
		public boolean uploadFile(int fd){
			
			/*get the upload file name from fd*/
			MyFile myfile = fd_file.get(fd);
			MyFile localmyfile = local_fd_file.get(fd);
			String path = myfile.path;
			String simpath = path;
			
			if(path.contains("-")){
		 		 simpath = path.split("-")[0];
		 	}
			boolean ret = false;
			
			try {
				RandomAccessFile raf = 
						new RandomAccessFile(localmyfile.file,"r");

				long len = raf.length();
				long cnt = 0;
				
				/*continuing call until all bytes are sent*/
                 while (cnt < len) {
                     int block = (int)Math.min(BLOCK, len - cnt);
                     byte[] b = new byte[block];
                     raf.read(b);
                     ret = server.uploadFile(simpath, b, cnt);
                     cnt += block;
                 }
                 raf.close();
                     
	            } catch (Exception e) {
	                e.printStackTrace();
	            }
	            return ret;
		}
		
		/**
		 * check if the file in correct version is in cache yet
		 * download it if is not in cache
		 * 
		 * @param simpath - the file need to be checked
		 * @return -1 if file is not on the server
		 * 		   -2 if the download function has fault
		 *         -3 if cache has no more capacity for new file
		 *          0 if all settings are good  
		 */
		public synchronized int callOnCheck(String simpath){
			
			int version = 1;
			long[] data = new long[2];
			Arrays.fill(data,-1);
			
			/* get file information fron the server*/
			try {
				data = server.getVerLen(simpath);
				version = (int)data[0];
			} catch (RemoteException e) {
				e.printStackTrace();
			}
			
			/*create version for map that is not exist yet*/
			if(version==-1){
				late_file.put(simpath, 1);
			}else{
				late_file.put(simpath, version);							
			}

			String path = simpath+"-"+late_file.get(simpath);
			String fullpath = proxydir+path;
			
			long len = data[1];
			File file = new File(fullpath);
			boolean ret = true;
			
			/*create directory for the file*/
			if(file.getParentFile()!=null && file.getParentFile().exists()
					==false) {
				new File(file.getParent()).mkdirs();
			}
			
			/*return -1 when the file is not exist on server*/
			if(version==-1){
				return -1;
			}
			
			/*cache doesn't have this file or version*/
			if( cache.contains(path)==false ){
					ret = false;
					/*clear cache and download file*/
					if(Proxy.cachemakespace(len,path)==true 
							&& (getFile(path,len)) ==true){
						cache.set(path, len);
						late_file.put(simpath, version);
						ret = true;
					}else{
						return -3;
					}
			}
		
			if(ret==false){
				return -2;
			}			
			return 0;
		}
		
		/**
		 * open function, open a file by option
		 * 
		 * @param path - the file need to be opened
		 * @param o - the open option
		 * @return file descriptor
		 */
		public synchronized int open( String path, OpenOption o ) {
			System.err.println("open: " +  path + " option : " + o);
			
			/*check if path is legal*/
			String snpath;
			try {
				snpath = new File(proxydir + path).getCanonicalPath();
				String prtpath = new File(proxydir).getCanonicalPath();
				if(!snpath.contains(prtpath)) {
					return Errors.ENOENT;
				}
			} catch (IOException e1) {
				e1.printStackTrace();
			}			
			path = simplifyPath(path);
			
			/*Check on Use*/
			int status = callOnCheck(path);
			if(status==-3){
				return Errors.ENOMEM;
			}
			
			/*add file version to name*/
			String verpath = path+"-"+late_file.get(path);
			String fullpath = proxydir + verpath;			
			MyFile myfile;
			File file;
			
			try{
				/*create instance for a file*/
				myfile = new MyFile();
				file = new File(fullpath);
				myfile.set(verpath, file, null);
				
				/*process by option*/
				switch(o){
					case CREATE_NEW:
						if(file.exists()==true || status!=-1){
							return Errors.EEXIST;
						}
						if(myfile.isDir==true){
							return Errors.EISDIR;
						}
						cache.set(verpath, 0);
						myfile.raf = new RandomAccessFile(file, "rw");
						server.createFile(path);
						break;
					case CREATE:
						if(myfile.isDir==true){
							return Errors.EISDIR;
						}
						if(status==-1){
							cache.set(verpath, 0);
							server.createFile(path);
						}
						myfile.raf = new RandomAccessFile(file, "rw");
						break;
					case READ:
						if(file.exists()==false || status==-1){
							return Errors.ENOENT;
						}
						if(file.isDirectory()){
					
						}else{
							myfile.raf = new  RandomAccessFile(file, "r");
						}
						myfile.ronly = true;
						break;
					case WRITE:
						if(file.exists()==false || status==-1){
							return Errors.ENOENT;
						}
						if(myfile.isDir==true){
							return Errors.EISDIR;
						}
						myfile.raf = new  RandomAccessFile(file, "rw");
						break;
					default:
						return Errors.EINVAL; 
				}

				/*Record file is open now*/
				if(open_file.containsKey(verpath)==false){
					open_file.put(verpath,1);
				}else{
					open_file.put(verpath, open_file.get(verpath)+1);
				}
				
				/*find the possible file descriptor*/
				int fd;
				if(o == OpenOption.READ){
					 fd = setFd(myfile,false);
				}else{
					myfile.modified = true;
					fd = setFd(myfile,true);
				}			
				return fd;
				
			}catch (Exception e){
				e.printStackTrace();
				return Errors.ENOENT;
			}
		}
		
		/**
		 * close function, open a file by option
		 * 
		 * @param fd - the file descriptor to be used
		 * @return 0 if close success, Errors if not
		 */
		public synchronized int close( int fd ) {
			if(fd_file.containsKey(fd)==false){
				return Errors.EBADF;
			}
			/*get the file instance*/
			MyFile myfile = fd_file.get(fd);
			String path = myfile.path;
			String simpath = path.split("-")[0];
			
			/*close process*/
			try{
				RandomAccessFile myraf = myfile.raf;
				if(myraf != null) myraf.close();
				
				if(open_file.get(path)==1){
					open_file.remove(path);
				}else{
					open_file.put(path, open_file.get(path)-1);
				}
				cache.update(myfile.path);

				/* if file is a create&write, upload the new version*/
				if(myfile.modified) {		
					/*get the copy file*/
					MyFile localmyfile = local_fd_file.get(fd);	
					RandomAccessFile localmyraf = localmyfile.raf;
					if(localmyraf!=null) localmyraf.close();
					
					/*upload the copy file*/
					if(!uploadFile(fd)) {
						return -1;	
					}
                    
					/*get the new version name of file*/
                    long len = localmyfile.file.length();
                    int newver = late_file.get(simpath) + 1;
                    late_file.put(simpath,newver);
                    myfile.modified = false;
                    String newpath = simpath+"-"+newver;
                    
                    /*clear copy from cache*/
                    cache.removebypath(localmyfile.path);    
                    if(open_file.containsKey(localmyfile.path)){
                        open_file.remove(localmyfile.path);
                    }
			   	    
                    /*Rename copy to new version name and put to cache*/
                    if(Proxy.cachemakespace(len,newpath)==true){
                        localmyfile.file.renameTo(new File(proxydir+newpath));
                        cache.set(newpath,len);                        
                    }else{
                        localmyfile.file.delete();
                    }      
				}
				/*remove file descriptor*/
				fd_file.remove(fd);
				if(local_fd_file.containsKey(fd)){
					local_fd_file.remove(fd);
				}			
			}catch (IOException e) {
				e.printStackTrace();
				return Errors.EBADF;
			}
			return 0;
		}
		
		/**
		 * simplify the path to make it simple
		 * 
		 * @param path - the original path
		 * @return the simple path
		 */
		public String simplifyPath(String path) {
	        
	        String[] parts = path.split("/");
	        Stack<String> st = new Stack<String>();
	        
	        /* seperate and process by '/' */
	        for(String part : parts){
	            if(part.equals(".") || part.length()==0) continue;
	            else if(part.equals("..") ){
	                if( st.empty()==false)
	                    st.pop();
	            }
	            else{
	                st.push(part);
	            }
	        }
	        if(st.empty()==true){
	            return "/";
	        }
	        /*reconstruct the path*/
	        StringBuffer sb = new StringBuffer();
	        while(st.empty()==false){
	            sb.insert(0,"/"+st.pop());
	        }
	        return sb.toString().substring(1, sb.toString().length());
		}
		 
		/**
		 * write function to write in file
		 * 
		 * @param fd - the file descriptor
		 * @param buf - the data need to be written
		 * @return the length of data write in
		 */
		public synchronized long write( int fd, byte[] buf ) {
			
			if(fd_file.containsKey(fd)==false){
				return Errors.EBADF;
			}
			MyFile myfile = fd_file.get(fd);
			
			if(myfile.ronly==true){
				return Errors.EBADF;
			}
			if(myfile.isDir==true){
				return Errors.EISDIR;
			}
			
			/*all write processes are to the local copy file*/
			MyFile localmyfile = local_fd_file.get(fd);
			try{
				RandomAccessFile localmyraf = localmyfile.raf;
				localmyraf.write(buf);
			}catch (IOException e) {
				e.printStackTrace();
				return Errors.EBADF;
			}
			return buf.length;
		}

		/**
		 * read function to read a file
		 * 
		 * @param fd - the file descriptor
		 * @param buf - the data need to be written
		 * @return the length of data read out
		 */
		public synchronized long read( int fd, byte[] buf ) {

			if(fd_file.containsKey(fd)==false){
				return Errors.EBADF;
			}
			long len;

			MyFile myfile = fd_file.get(fd);
			System.err.println("read file : " +  myfile.path);
			if(myfile.isDir==true){
				return Errors.EISDIR;
			}
			
			/*read the original file*/
			try{
				RandomAccessFile myraf = myfile.raf;
				len = myraf.read(buf);
			}catch (IOException e) {
				e.printStackTrace();
				return Errors.EBADF;
			}
			if(len<0){
				len = 0;
			}
			System.err.println("return read: " +  len);
				return len;
		}

		/**
		 * lseek function to lseek to a location
		 * 
		 * @param fd - the file descriptor
		 * @param pos - the position difference
		 * @param o - the lseek options
		 * @return the current position
		 */
		public synchronized long lseek( int fd, long pos, LseekOption o ) {
			if(fd_file.containsKey(fd)==false){
				return Errors.EBADF;
			}
			
			MyFile myfile = fd_file.get(fd);
			
			if(myfile.isDir==true){
				return Errors.EISDIR;
			}

			/*to find the correct position accoding to option*/
			long ret;
			try{
				RandomAccessFile myraf = myfile.raf;
				switch(o){
				case FROM_CURRENT:
					ret = pos + myraf.getFilePointer();	
					break;
				case FROM_END:
					ret = myraf.length() - pos;
					break;
				case FROM_START:
					ret = pos;
					break;
				default:
					return Errors.EINVAL;
				}
				myraf.seek(ret);
				if(local_fd_file.containsKey(fd)){
					RandomAccessFile localmyraf = local_fd_file.get(fd).raf;
					localmyraf.seek(ret);
				}			
			}catch (IOException e) {
				e.printStackTrace();
				return Errors.EBADF;
			}
			return ret;
		}

		/**
		 * unlink function to delete file on server
		 * 
		 * @param path - the file name need to be deleted
		 * @return the delete result status from server
		 */
		public synchronized int unlink( String path ) {
			int version = 1;
			if(late_file.containsKey(path)){
				version = late_file.get(path);
			}
			String verpath = path+"-"+version;
			String fullpath = proxydir + verpath;

			/*check the path and delete file from server*/
			try {
				File file = new File(fullpath);
				if(file.isDirectory()) {
					return Errors.EISDIR;
				}
				
				if(open_file.containsKey(verpath)){
					return Errors.EBUSY;
				}
				
				if(file.exists()==true) {
					file.delete();
				}
				
				if( server.rmFile(path) ==false) {
					return Errors.ENOENT;
				}
			}
			catch(Exception e) {
				e.printStackTrace();
				return Errors.ENOENT;
			}
			return 0;
		}
	
		/**
		 * client leaves the cache
		 */
		public synchronized void clientdone() {
			return;
		}
		
		/**
		 * find the available file descriptor and 
		 * create copy if need
		 * 
		 * @param myfile - the file which needs a descriptor
		 * @param needlocal - if the file needs a copy to edit
		 * @return the file descriptor assigned
		 */
	   public int setFd(MyFile myfile, boolean needlocal) 
			   throws FileNotFoundException {
		   
		   synchronized(lock){ 
			   for (int i = 3; ; i++) {
	                if (!FileHandler.fd_file.containsKey(i)) {
	                	/*assign the fd to file*/
	                	fd_file.put(i, myfile);
	                	if(needlocal){
	                		
	                		/*read original file information*/
	                		MyFile localmyfile = new MyFile();
	                		File localfile = 
	                			new File(proxydir+ myfile.path+"-"+i);
	                		local_fd_file.put(i, localmyfile);
	                		
	                		/*make a copy of file and put in cache*/
		                	try {
		                		if(myfile.file.exists()){
		                			Files.copy(
            					myfile.file.toPath(), localfile.toPath(),
                					StandardCopyOption.REPLACE_EXISTING
                					);
		                		}
							} catch (IOException e) {
								e.printStackTrace();
							}
		                	/*set up copy file and record it*/
		                	RandomAccessFile localraf = 
		                			new RandomAccessFile(localfile, "rw");
	                		localmyfile.set(
	                				myfile.path+"-"+i, 
	                				localfile, localraf
	                				);
	                		open_file.put(localmyfile.path, 1);
	                		
	                		if(Proxy.cachemakespace(
	                				localfile.length(),localmyfile.path)
	                				==true){
	                			cache.set(localmyfile.path, localfile.length());
	                		}
	                		
	                	}
	                	return i;
	                }
	           }
		   }
       }
	   
	}
	/**
	 * Open a new handler for coming client
	 * 
	 */
	private static class FileHandlingFactory implements FileHandlingMaking{
			public FileHandling newclient() {
				return new FileHandler();
			}
		}
	
	
	/**
	 * main function to set up the proxy
	 * @param ip - the ip address of server
	 * @param port - the communication port of server
	 * @param dir - the directory address of the proxy
	 * @param capacity - the size of cache
	 * 
	 */
 	public static void main(String[] args) throws IOException {
		if(args.length < 4) {
			System.err.println("Proxy wrong parameters");
			return;
		}
			
		/*get parameters from the input*/
		serverip = args[0];
		serverport = Integer.parseInt(args[1]);
		proxydir = args[2];
		cachesize = Long.parseLong(args[3]);
		String serversocket = null;
		cache = new LRUCache(cachesize);
			 		
		if (proxydir.charAt(proxydir.length()-1) != '/') {
			proxydir += '/';
		}
			
		/*connect to server*/
		try{
		   serversocket = 
			String.format("//%s:%d/ServerService", serverip, serverport);
		   server = (ServerInterface) Naming.lookup(serversocket);
		}catch (Exception e) {
			e.printStackTrace();
		}
		
		System.out.println("Hello World");
		(new RPCreceiver(new FileHandlingFactory())).run();
	}	
}

