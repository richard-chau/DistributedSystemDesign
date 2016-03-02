import java.rmi.*;
import java.io.*;
/**
 * InterFace for remote server
 * @author haoyangy
 */

/*** ServerInterface.java ***/
public interface ServerInterface extends Remote
{
	/*Interface functions*/
	public boolean rmFile(String path) throws RemoteException;
	public byte[] getFile(String path, long cnt, int block) throws RemoteException;
	public boolean uploadFile(String path, byte[] buffer, long cnt) throws RemoteException;
	public boolean createFile(String path) throws RemoteException;
	public long[] getVerLen(String path) throws RemoteException;
}