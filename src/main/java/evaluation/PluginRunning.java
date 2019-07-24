package evaluation;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import cellTracking.Cell_Tracker;
import ij.IJ;
import ij.ImagePlus;

public class PluginRunning {
	
	public void runPluginOnImagePathToFolder(String path, String resultsFolder, boolean copyImage) throws IOException {
		ImagePlus imp = new ImagePlus(path);
		runPluginOnImageToFolder(imp, resultsFolder, copyImage);
	}

	public void runPluginOnImageToFolder(ImagePlus imp, String resultsFolder, boolean copyImage) throws IOException {
		Path newdir = Paths.get(resultsFolder);
		if (copyImage) {			
			Path name = newdir.getName(newdir.getNameCount()-1);
			IJ.save(imp, newdir.resolve(name).toString());
		}
		Cell_Tracker tracker = new Cell_Tracker();
		tracker.setup("no save", imp);
		tracker.setParameters(imp, 0.5f, 60, 1500, 0.5f, 1.0f, 4, true, true, false, true);
		tracker.runOnImagePlus(imp);
		tracker.setup("final", imp);
		
		String txtPath = tracker.textResultsPath();
		String tifPath = tracker.tifResultPath();
		String infoFilePath = tracker.mitosisInfoFilePath();
		System.out.println(txtPath);
		System.out.println(tifPath);
		System.out.println(infoFilePath);
		
		Path txt = Paths.get(txtPath);
		Path tif = Paths.get(tifPath);
		Path infoPath = Paths.get(infoFilePath);
		
		Files.move(txt, newdir.resolve(txt.getFileName()), StandardCopyOption.REPLACE_EXISTING);
		Files.move(tif, newdir.resolve(tif.getFileName()), StandardCopyOption.REPLACE_EXISTING);
		Files.move(infoPath, newdir.resolve(infoPath.getFileName()), StandardCopyOption.REPLACE_EXISTING);
		
		String log = "Files moved from " + tifPath + "to " + newdir.resolve(tif.getFileName()).toString();
		IJ.log(log);
	}
	
	static List<Path> getAllFilePathsFromFolders(List<String> folders) throws IOException {		
		List<Path> result = new ArrayList<Path>();
		for (String dir: folders) {
			try (Stream<Path> paths = Files.walk(Paths.get(dir))) {
			    List<Path> listPaths = paths.filter(Files::isRegularFile).collect(Collectors.toList());
			    result.addAll(listPaths);
			}
		}
		
		return result;
	}
	
	static List<Path> sequencesList() throws IOException {
		ArrayList<String> folders = new ArrayList<String>();
		// folders with confocal data
		folders.add("C:\\Tokyo\\Confocal\\171228A1-tiff-combinedAB\\c2");
		folders.add("C:\\Tokyo\\Confocal\\171228A1-tiff-combinedCD\\c2");
		folders.add("C:\\Tokyo\\Confocal\\171228A1-tiff-combinedEF\\c2");
		folders.add("C:\\Tokyo\\Confocal\\181221-q8146921-tiff\\c2");
		folders.add("C:\\Tokyo\\Confocal\\181221-q8156901-tiff\\c2");
		folders.add("C:\\Tokyo\\Confocal\\181228A1-tiff-combined\\c2");
		
		// fluo data
		folders.add("C:\\Tokyo\\Data\\170704DataSeparated\\C0002\\all_folder");
		return getAllFilePathsFromFolders(folders);
	}
	
	static List<Path> testSequences() throws IOException {
		ArrayList<String> folders = new ArrayList<String>();
		folders.add("C:\\Tokyo\\Data\\Short Sequences Test");
		return getAllFilePathsFromFolders(folders);
	}
	
	static List<Path> exampleSequences() throws IOException {
		ArrayList<String> folders = new ArrayList<String>();
		folders.add("C:\\Tokyo\\Example Sequences (segmented)");
		return getAllFilePathsFromFolders(folders);
	}
	
	static Path removeExtenstionFromPath(Path path) {
		String str = path.toString();
	    if (str.indexOf(".") > 0)
	    	str = str.substring(0, str.lastIndexOf("."));
	    return Paths.get(str);
	}
	
	public static void main(String[] args) {
		PluginRunning plugin = new PluginRunning();
		Path masterFolder = Paths.get("C:\\Tokyo\\auto_results");
		boolean copyImage = true;
		try {
			 List<Path> paths = sequencesList();
			 paths = testSequences();
			 for(Path p: paths) {
				 //create folder for results based on sequence name
				 Path name = removeExtenstionFromPath(p);
				 Path resFolder = masterFolder.resolve(name.getFileName());
				 if (!Files.exists(resFolder)) { //if no folder
					 if (!(new File(resFolder.toString()).mkdirs())) { //create it
					 	System.out.println("Failed to make a directory " + resFolder.toString());
					 	continue;
					 }
					 System.out.println("Folder created " + resFolder);
				 }
				 System.out.println("Running plugin on " + p.toString());
				 plugin.runPluginOnImagePathToFolder(p.toString(), resFolder.toString(), copyImage);
			 }
			 System.out.println("Finished");
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}
