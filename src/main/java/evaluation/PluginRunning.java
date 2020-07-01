package evaluation;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import TechnicalMeasurementsCTC.Metrics;
import TechnicalMeasurementsCTC.WriterCSV;
import cellTracking.Cell_Tracker;
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.process.ImageProcessor;
import properties.CTCResultStruct;
import visualization.Visualization;

public class PluginRunning {
	
	public boolean createFolder(Path folder) {
		if (!Files.exists(folder)) { // if no folder
			if (!(new File(folder.toString()).mkdirs())) { // create it
				System.out.println("Failed to make a directory " + folder.toString());		
				return false;
			}
			System.out.println("Folder created " + folder);
		}
		return true;
	}


	public void runPluginOnImagePathToFolder(String path, String resultsFolder, PluginParameters params, boolean tryLoadSegmentation,
			boolean copyImage, boolean saveInCTCFormat, boolean removeBorderComponents) throws IOException {
		ImagePlus imp = new ImagePlus(path);
		runPluginOnImageToFolder(imp, resultsFolder, params, tryLoadSegmentation, copyImage, saveInCTCFormat, removeBorderComponents);
	}


	public void runPluginOnImageToFolder(ImagePlus imp, String resultsFolder, PluginParameters params, boolean tryLoadSegmentation,
			boolean copyImage, boolean saveInCTCFormat, boolean removeBorderComponents) throws IOException {

		CTCResultStruct struct = new CTCResultStruct();
		struct.fillByResultsFolder(resultsFolder);
		
		Path resDir = Paths.get(resultsFolder);
		if (copyImage) {
			Path name = resDir.getName(resDir.getNameCount() - 1);
			IJ.save(imp, resDir.resolve(name).toString());
		}
		System.out.println(imp.getStackSize());
		Cell_Tracker tracker = new Cell_Tracker();
		tracker.setup("no save", imp);
		tracker.setParameters(imp, params);
//		tracker.runOnImagePlus(imp);
		if (tryLoadSegmentation) {
			if (struct.segmentation != null) {
				System.out.println("--- Previous segmentation loaded.");
				tracker.loadSegmentationByImage(struct.segmentation, imp, removeBorderComponents);
			}
			else {
				tracker.runOnImagePlus(imp);
			}
		}
		if (!tryLoadSegmentation) {
			tracker.runOnImagePlus(imp);
		}
			
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

		Files.move(txt, resDir.resolve(txt.getFileName()), StandardCopyOption.REPLACE_EXISTING);
		Files.move(tif, resDir.resolve(tif.getFileName()), StandardCopyOption.REPLACE_EXISTING);
		Files.move(infoPath, resDir.resolve(infoPath.getFileName()), StandardCopyOption.REPLACE_EXISTING);

		String log = "Files moved from " + tifPath + "to " + resDir.resolve(tif.getFileName()).toString();
		
		if (saveInCTCFormat) {
			// make extra folder, and save there tracking results in CTC required format
			String ctcFolderName = "CTC";
			Path ctcFolderPath =  resDir.resolve(ctcFolderName);
			createFolder(ctcFolderPath);
			Files.copy(resDir.resolve(txt.getFileName()), ctcFolderPath.resolve("res_track.txt"), StandardCopyOption.REPLACE_EXISTING);
			
			//save segmentation stack
			ImageStack segStack = IJ.openImage(resDir.resolve(tif.getFileName()).toString()).getStack();
			ImagePlus imp2 = new ImagePlus();
			int nSlices = segStack.getSize();
			String name = "mask";
			for (int i=1; i<=nSlices; i++) {
				ImageProcessor ip = segStack.getProcessor(i);
				String digits = String.format("%03d",i-1);
				imp2.setProcessor(null, ip);
				Path path = ctcFolderPath.resolve(name+digits+".tif");
				IJ.saveAs(imp2, "tiff", path.toString());
			}
		}
		IJ.log(log);
	}	

	public static List<Path> getAllFilePathsFromFolders(List<String> folders) throws IOException {
		List<Path> result = new ArrayList<Path>();
		for (String dir : folders) {
			try (Stream<Path> paths = Files.walk(Paths.get(dir),1)) {
				List<Path> listPaths = paths.filter(Files::isRegularFile).collect(Collectors.toList());
				result.addAll(listPaths);
			}
		}
		return result;
	}

	static List<Path> sequencesList(boolean includeConfocal, boolean includeFluorescence) throws IOException {
		ArrayList<String> folders = new ArrayList<String>();
		// folders with confocal data
		if (includeConfocal) {
			folders.add("C:\\Tokyo\\Confocal\\171228A1-tiff-combinedAB\\c2");
			folders.add("C:\\Tokyo\\Confocal\\171228A1-tiff-combinedCD\\c2");
			folders.add("C:\\Tokyo\\Confocal\\171228A1-tiff-combinedEF\\c2");
			folders.add("C:\\Tokyo\\Confocal\\181221-q8146921-tiff\\c2");
			folders.add("C:\\Tokyo\\Confocal\\181221-q8156901-tiff\\c2");
			folders.add("C:\\Tokyo\\Confocal\\181228A1-tiff-combined\\c2");
		}

		// fluo data
		if (includeFluorescence) {
			folders.add("C:\\Tokyo\\Data\\170704DataSeparated\\C0002\\all_folder");
		}
		return getAllFilePathsFromFolders(folders);
	}

	static List<Path> shortTestSequences() throws IOException {
		ArrayList<String> folders = new ArrayList<String>();
		folders.add("C:\\Tokyo\\Data\\Short Sequences Test");
		return getAllFilePathsFromFolders(folders);
	}

	static List<Path> hasGTSequences() throws IOException {
		ArrayList<String> folders = new ArrayList<String>();
		folders.add("C:\\Tokyo\\Example Sequences (segmented)");
		return getAllFilePathsFromFolders(folders);
	}
	
	/** returns dictionary of sequence names and their GT paths */
	static HashMap<String, Path> GTPaths() {
		HashMap<String, Path> res = new HashMap<String, Path>();
		res.put("c0010901_easy_ex", Paths.get("C:\\Tokyo\\metrics\\c0010901_easy_ex\\GT"));
		res.put("c0010906_medium_double_nuclei_ex", Paths.get("C:\\Tokyo\\metrics\\c0010906_medium_double_nuclei_ex\\GT"));
		res.put("c0010907_easy_ex", Paths.get("C:\\Tokyo\\metrics\\c0010907_easy_ex\\GT"));
		res.put("c0010913_hard_ex", Paths.get("C:\\Tokyo\\metrics\\c0010913_hard_ex\\GT"));
		return res;
	}
		

	static Path removeExtensionFromPath(Path path) {
		String str = path.toString();
		if (str.indexOf(".") > 0)
			str = str.substring(0, str.lastIndexOf("."));
		return Paths.get(str);
	}

	public static void main(String[] args) {
		PluginRunning plugin = new PluginRunning();
		// Path masterFolder = Paths.get("C:\\Tokyo\\auto_results");
		boolean copyImage = true;
		boolean drawOnly = false;
		boolean rmBorderComponents = true;

		Path wshedResults = Paths.get("C:\\Tokyo\\watershed_results");
		Path noWshedResults = Paths.get("C:\\Tokyo\\no_watershed_results");
		Path noMitosisWshedResults = Paths.get("C:\\Tokyo\\no_mitosis_watershed_results");
		Path noMitosisNoWshedResults = Paths.get("C:\\Tokyo\\no_mitosis_no_watershed_results");
		
//		Path olderWshedUnetResults = Paths.get("wshed v0");
//		Path olderWshedUnetResults = Paths.get("C:\\Tokyo\\pre_mitosis wshed resnet");
		Path olderWshedUnetResults = Paths.get("C:\\Tokyo\\after mitosis wshed resnet (mit not used, old)");
		
		PluginParameters paramsWithWatershed = new PluginParameters("with wshed remove border",
				0.5f, 50, 1500, 0.45f, 1.0f, 4, true, true, true, 
				rmBorderComponents,
				false, true, true, wshedResults);
		PluginParameters paramsWithoutWatershed = new PluginParameters("no wshed",
				0.5f, 50, 1500, 0.5f, 1.0f, 4, true, true, false, 
				rmBorderComponents,
				false, true, true, noWshedResults);
		
		PluginParameters paramsWatershedNoMitosis = new PluginParameters("no mitosis with wshed",
				0.5f, 50, 1500, 0.5f, 1.0f, 4, false, true, true, 
				rmBorderComponents,
				false, true, true, noMitosisWshedResults);
		PluginParameters paramsNoWatershedNoMitosis = new PluginParameters("no mitosis no wshed",
				0.5f, 50, 1500, 0.5f, 1.0f, 4, false, true, false, 
				rmBorderComponents,
				false, true, true, noMitosisNoWshedResults);
		
		PluginParameters paramsWithWatershedKeepBorder = new PluginParameters("with wshed keep border",
				0.5f, 50, 1500, 0.55f, 1.0f, 3, true, true, true, 
				!rmBorderComponents,
				false, true, true, olderWshedUnetResults);
		

		ArrayList<PluginParameters> parametersList = new ArrayList<PluginParameters>();
		//parametersList.add(paramsWithoutWatershed);
//		parametersList.add(paramsWithWatershed);
		//parametersList.add(paramsWatershedNoMitosis);
		//parametersList.add(paramsNoWatershedNoMitosis);
		parametersList.add(paramsWithWatershedKeepBorder);
		
		boolean tryLoadSegmentation = false;
		
		try {
			String resultFileName = "wshed_resunet_mit_added_full_v6_len3";
			WriterCSV writer = new WriterCSV(Paths.get("C:\\Tokyo\\metrics\\" + resultFileName + ".csv"));
			String[] header = {"Sequence", "Experiment", "SEG", "TRA", "DET", "CT", "TF", "BCi"};
			writer.writeLine(header);
			for (int i = 0; i < parametersList.size(); ++i) {
				Path masterFolder = parametersList.get(i).destinationFolder;
				PluginParameters params = parametersList.get(i);
				
				Metrics metrics = new Metrics();

				List<Path> paths = hasGTSequences();
				List<Path> testP = new ArrayList<Path>();
				testP.add(paths.get(2));
				paths = testP;
				HashMap<String, Path> gtPaths = GTPaths();
				//List<Path> paths = shortTestSequences();
				//paths.addAll(sequencesList(true, true));
				
				for (Path p : paths) {
					// create folder for results based on sequence name
					//p = Paths.get("C:\\Tokyo\\Example Sequences (segmented)\\c0010901_easy_ex.tif");
					Path name = removeExtensionFromPath(p);
					Path resFolder = masterFolder.resolve(name.getFileName());
					if (!Files.exists(resFolder)) { // if no folder
						if (!(new File(resFolder.toString()).mkdirs())) { // create it
							System.out.println("Failed to make a directory " + resFolder.toString());
							continue;
						}
						System.out.println("Folder created " + resFolder);
					}
					System.out.println("Running plugin on " + p.toString());
					if (!drawOnly) {
						params.destinationFolder = resFolder;
						plugin.runPluginOnImagePathToFolder(p.toString(), resFolder.toString(), params,
								tryLoadSegmentation,
								copyImage, true,
								params.removeBorderDetections);
						// calculate SEG, TRA here
						// first, change save format - add saving as image sequence+txt file in a separate folder for ctc format
						// which is done earlier in runPlugin
						
						//now results 
						String seqName = name.getFileName().toString();
						double SEG, TRA, DET, CT, TF, BCi;
						if (gtPaths.containsKey(seqName)) {
							// calculate metrics
							metrics.calculateMetrics(gtPaths.get(seqName), resFolder.resolve("CTC"));
							SEG = metrics.SEG();
							TRA = metrics.TRA();
							DET = metrics.DET();
							CT = metrics.CT();
							TF = metrics.TF();
							BCi = metrics.BCi();
							String[] line = new String[8];
							line[0] = seqName;
							line[1] = parametersList.get(i).name;
							line[2] = String.format("%.4f", SEG);
							line[3] = String.format("%.4f", TRA);
							line[4] = String.format("%.4f", DET);
							line[5] = String.format("%.4f", CT);
							line[6] = String.format("%.4f", TF);
							line[7] = String.format("%.4f", BCi);
							writer.writeLine(line);
						}

						Visualization.drawAndSaveMitosisByResultFolder(resFolder.toString());
					} else {
						Visualization.drawAndSaveMitosisByResultFolder(resFolder.toString());
					}
				}
				System.out.println("Finished running on folder " + masterFolder.toString());
				
			}
			writer.closeWriter();
			System.out.println("Finished");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
