package TechnicalMeasurementsCTC;

import java.nio.file.Path;

import org.scijava.ItemIO;
import org.scijava.log.LogService;
import org.scijava.log.StderrLogService;
import org.scijava.plugin.Parameter;

import de.mpicbg.ulman.ctc.workers.TRA;
import de.mpicbg.ulman.ctc.workers.SEG;
import de.mpicbg.ulman.ctc.workers.DET;
import de.mpicbg.ulman.ctc.workers.CT;
import de.mpicbg.ulman.ctc.workers.TF;
import de.mpicbg.ulman.ctc.workers.BCi;
import de.mpicbg.ulman.ctc.workers.CCA;

public class Metrics {
	
	private double _SEG;
	private double _TRA;
	private double _DET;
	private double _CT;
	private double _TF;
	private double _BCi;
	private double _CCA;
	
	private LogService log;

	public Metrics() {
		_SEG = -1;
		_TRA = -1;
		_DET = -1;
		_CT = -1;
		_TF = -1;
		_BCi = -1;
		_CCA = -1;
		log = new StderrLogService();
	}
	
	public double SEG() {
		return _SEG;
	}	
	public double TRA() {
		return _TRA;
	}	
	public double DET() {
		return _DET;
	}	
	public double CT() {
		return _CT;
	}
	public double TF() {
		return _TF;
	}	
	public double BCi() {
		return _BCi;
	}	
	public double CCA() { //not used - we don't have complete cell cycle in data
		return _CCA;
	}
	
	public void calculateMetrics(Path GTFolderPath, Path resultPath) {
		String GTdir  = GTFolderPath.toString();
		String RESdir = resultPath.toString();
		calculateMetrics(GTdir, RESdir);
	}
	
	
	public void calculateMetrics(String GTdir, String RESdir) {		
		// SEG
		try {
			final SEG seg = new SEG(log);
			seg.doLogReports = false;
			seg.noOfDigits = 3;
			_SEG = seg.calculate(GTdir, RESdir);
		}
		catch (RuntimeException e) {
			log.error("CTC SEG measure problem: "+e.getMessage());
		}
		catch (Exception e) {
			log.error("CTC SEG measure error: "+e.getMessage());
		}
		
		// TRA
		try {
			final TRA tra = new TRA(log);
			tra.doConsistencyCheck = true;
			tra.doLogReports = false;
			tra.noOfDigits = 3;
			_TRA = tra.calculate(GTdir, RESdir);
		}
		catch (RuntimeException e) {
			log.error("CTC TRA measure problem: "+e.getMessage());
		}
		catch (Exception e) {
			log.error("CTC TRA measure error: "+e.getMessage());
		}
		
		// DET
		try {
			final DET det = new DET(log);
			det.doConsistencyCheck = true;
			det.doLogReports = false;
			det.noOfDigits = 3;
			_DET = det.calculate(GTdir, RESdir);
		}
		catch (RuntimeException e) {
			log.error("CTC DET measure problem: "+e.getMessage());
		}
		catch (Exception e) {
			log.error("CTC DET measure error: "+e.getMessage());
		}
		
		// CT
		try {
			final CT ct = new CT(log);
			_CT = ct.calculate(GTdir, RESdir);
		}
		catch (RuntimeException e) {
			log.error("CTC CT measure problem: "+e.getMessage());
		}
		catch (Exception e) {
			log.error("CTC CT measure error: "+e.getMessage());
		}
		
		// TF
		try {
			final TF tf = new TF(log);
			_TF = tf.calculate(GTdir, RESdir);
		}
		catch (RuntimeException e) {
			log.error("CTC TF measure problem: "+e.getMessage());
		}
		catch (Exception e) {
			log.error("CTC TF measure error: "+e.getMessage());
		}
		
		// BCi
		try {
			final BCi bci = new BCi(log);
			bci.setI(5);
			_BCi = bci.calculate(GTdir, RESdir);
		}
		catch (RuntimeException e) {
			log.error("CTC BCi measure problem: "+e.getMessage());
		}
		catch (Exception e) {
			log.error("CTC BCi measure error: "+e.getMessage());
		}
		
		// CCA
//		try {
//			final CCA cca = new CCA(log);
//			_CCA = cca.calculate(GTdir, RESdir);
//		}
//		catch (RuntimeException e) {
//			log.error("CTC CCA measure problem: "+e.getMessage());
//		}
//		catch (Exception e) {
//			log.error("CTC CCA measure error: "+e.getMessage());
//		}
//				
		log.info(_SEG);
		log.info(_TRA);
		log.info(_DET);
		log.info(_CT);
		log.info(_TF);
		log.info(_BCi);
//		log.info(_CCA);
	}
}
