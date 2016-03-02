import java.io.*;

/**
 * MyFile to record file information
 * @author haoyangy
 */


/*** MyFile.java ***/
class MyFile {
	
	public String path;
	public File file;
	public RandomAccessFile raf;
	public boolean isDir;
	public boolean ronly;
	public boolean modified = false;
	
	MyFile() {
		path = null; //simplified path without Rootdir
		file = null; //File associated with path
		raf = null;  //raf if it's file
		ronly = false; //read only defined 
		isDir = false; //if is Dir
	}
	
	/**
	 * set the file information to the instance
	 * 
	 * @param path - the relative file name
	 * @param file - the File class with absolute file name
	 * @param raf - the RandomAccessFile to the file
	 */
	public void set(String path,File file, RandomAccessFile raf){
		if(path!=null){
			this.path = path;
		}
		if(file!=null){
			this.file = file;
		}
		if(raf!=null){
			this.raf = raf;
		}
		if(file.isDirectory()){
			this.isDir = true;
		}
	}
	

	
}

