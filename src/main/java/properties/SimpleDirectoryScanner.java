package properties;

import java.io.File;

/* a class for simple search of files in a directory */
public class SimpleDirectoryScanner {
	String _dir;
	
	public void setDirectory(String dir) {
		if (!dir.endsWith("/"))
			dir = dir + "/";
		_dir = dir;
	}
	
	public String fileNameBySuffix(String suffix) {
		String result = "";
		File folder = new File(_dir);
		    for (final File fileEntry : folder.listFiles()) {
		    	String name = fileEntry.getName(); 
		    	if (name.endsWith(suffix)) {
		    		result = name;
		    		break;
		    	}		    		
		    }
		return result;
	}
}
