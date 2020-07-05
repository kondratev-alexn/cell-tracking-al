package TechnicalMeasurementsCTC;

import java.nio.file.Paths;

public class MetricsTest {

	public static void main(String[] args) {
		String gtPaths[] = new String[4];
		String resPaths[] = new String[4];
		String seqNames[] = new String[4];
		
		String resFolders[] = {"RES", "RES Unet", "RES UNet boundary no mit no wshed", "RES UNet boundary no mit wshed",
				"RES UNet boundary no wshed", "Res UNet boundary wshed"};
		
		gtPaths[0] = "C:\\Tokyo\\metrics\\c0010901_easy_ex\\GT";
		gtPaths[1] = "C:\\Tokyo\\metrics\\c0010906_medium_double_nuclei_ex\\GT";
		gtPaths[2] = "C:\\Tokyo\\metrics\\c0010907_easy_ex\\GT";
		gtPaths[3] = "C:\\Tokyo\\metrics\\c0010913_hard_ex\\GT";		

		resPaths[0] = "C:\\Tokyo\\metrics\\c0010901_easy_ex";
		resPaths[1] = "C:\\Tokyo\\metrics\\c0010906_medium_double_nuclei_ex";
		resPaths[2] = "C:\\Tokyo\\metrics\\c0010907_easy_ex";
		resPaths[3] = "C:\\Tokyo\\metrics\\c0010913_hard_ex";
		
		seqNames[0] = "c0010901_easy_ex";
		seqNames[1] = "c0010906_medium_double_nuclei_ex";
		seqNames[2] = "c0010907_easy_ex";
		seqNames[3] = "c0010913_hard_ex";
		

		WriterCSV writer = new WriterCSV(Paths.get("C:\\Tokyo\\metrics\\" + "old_results_t" + ".csv"));
		String[] header = {"Sequence", "Experiment", "SEG", "TRA", "DET", "CT", "TF", "BCi"};
		writer.writeLine(header);
		
		for (int i=0; i<4; ++i) {
			String gtPath = gtPaths[i];
			String resPath = resPaths[i];
			
			for (int j=0; j<resFolders.length; ++j) {
				Metrics metrics = new Metrics();
				
				String seqName = seqNames[i];
				double SEG, TRA, DET, CT, TF, BCi;
				// calculate metrics
				metrics.calculateMetrics(gtPath, resPath+"\\"+resFolders[j]);
				SEG = metrics.SEG();
				TRA = metrics.TRA();
				DET = metrics.DET();
				CT = metrics.CT();
				TF = metrics.TF();
				BCi = metrics.BCi();
				String[] line = new String[8];
				line[0] = seqName;
				line[1] = resFolders[j];
				line[2] = String.format("%.4f", SEG);
				line[3] = String.format("%.4f", TRA);
				line[4] = String.format("%.4f", DET);
				line[5] = String.format("%.4f", CT);
				line[6] = String.format("%.4f", TF);
				line[7] = String.format("%.4f", BCi);
				writer.writeLine(line);	
			}
		}
		
		writer.closeWriter();
		System.out.println("Finished");
	}

}
