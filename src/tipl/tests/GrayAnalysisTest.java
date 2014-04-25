package tipl.tests;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Hashtable;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import tipl.formats.TImgRO;
import tipl.tools.ComponentLabel;
import tipl.tools.GrayAnalysis;
import tipl.util.CSVFile;
import tipl.util.D3float;
import tipl.util.ITIPLPlugin;
import tipl.util.ITIPLPluginIO;
import tipl.util.ITIPLPluginIn;
import tipl.util.TIPLGlobal;
import tipl.util.TImgTools;

/**
 * Test the GrayAnalysis and CSVFile classes
 * 
 * @author mader
 * 
 */
public class GrayAnalysisTest {
	protected static void showLine(Hashtable<String,String> cLine, int lineNo) {
		String outString="";

		for(String cEle : cLine.keySet()) {
			outString+=cEle+":"+cLine.get(cEle)+", ";

		}
		System.out.println(lineNo+"\t"+outString);
	}
	protected static int[] checkFile(String fileName, boolean verbose) {

		int[] rowCols=new int[2];
		final CSVFile insFile = CSVFile.FromPath(fileName, 2);
		// get the column count
		Hashtable<String,String> cLine = insFile.lineAsDictionary();
		rowCols[1]=cLine.size();
		int i=1;
		if(verbose) showLine(cLine,i);


		while (!insFile.fileDone) {
			cLine = insFile.lineAsDictionary();
			i++;
			if(verbose) showLine(cLine,i);
			
			
		}
		rowCols[0]=i;
		return rowCols;

	}

	//@Test
	public void testCSVStringRead() {
		final CSVFile insFile = CSVFile.FromString(testCSVData.split("\n"), 2);
		Hashtable<String,String> cLine = insFile.lineAsDictionary();

		assertEquals("cola",insFile.getHeader()[0]);
		assertEquals("colb",insFile.getHeader()[1]);
		assertEquals("colc",insFile.getHeader()[2]);

		assertEquals(3,cLine.size());
		int i=1;
		if(true) showLine(cLine,i);


		while (!insFile.fileDone) {
			cLine = insFile.lineAsDictionary();
			i++;
			if(true) showLine(cLine,i);
			
		}
		assertEquals(4,i);

	}
	final public static String testCSVData="I am just junk\nCOLA,COLB,COLC\n1,2,3\n4,5,6\n7,8,9\n7,8,9\n";

	/**
	 * Test method for {@link tipl.tools.CSVFile}.
	 */
	@Test
	public void testCSVFileRead() {
		try {
			FileWriter testWriter = new FileWriter(tempFilePathCSV);
			testWriter.write(testCSVData);
			testWriter.close();
		} catch (IOException e) {
			throw new IllegalArgumentException("create the needed temporary file");
		}

		// check the output file itself
		int[] rowCols=checkFile(tempFilePathCSV,true);
		assertEquals(4,rowCols[0]);
		assertEquals(3,rowCols[1]);

	}

	protected static double getAvg(final ITIPLPluginIn iP) {
		return ((Double) iP.getInfo("avgcount")).doubleValue();
	}

	protected static int getMax(final ITIPLPluginIn iP) {
		return ((Integer) iP.getInfo("maxlabel")).intValue();
	}

	protected static TImgRO makeCL(final TImgRO sImg) {
		final ITIPLPluginIO CL = new ComponentLabel();
		CL.LoadImages(new TImgRO[] { sImg });
		CL.execute();
		return CL.ExportImages(sImg)[0];
	}


	protected static ITIPLPlugin doLacunaAnalysis(final TImgRO labelImage,final String outFile) {


		ITIPLPlugin cGA=GrayAnalysis.StartLacunaAnalysis(labelImage,outFile,"First Run");
		if (TIPLGlobal.getDebug()) {
			System.out.println("Groups:"+cGA.getInfo("groups"));
			System.out.println("Average X Position:"+cGA.getInfo("average,meanx"));
			System.out.println("Average Y Position:"+cGA.getInfo("average,meany"));
			System.out.println("Average Z Position:"+cGA.getInfo("average,meanz"));
		}
		return cGA;
	}

	/**
	 * Test method for {@link tipl.tools.GrayAnalysis#StartLacunaAnalysis}.
	 */
	@Test
	public void testLA() {
		System.out.println("Testing execute");
		final TImgRO testImg = TestPosFunctions.wrapIt(10,
				new TestPosFunctions.SheetImageFunction());
		final TImgRO labelImage = makeCL(testImg);
		ITIPLPlugin cGA = doLacunaAnalysis(labelImage,tempFilePath);

		assertEquals(5.0,((Double) cGA.getInfo("average,meanx")).doubleValue(),0.1);
		assertEquals(4.5,((Double) cGA.getInfo("average,meany")).doubleValue(),0.1);
		assertEquals(4.5,((Double) cGA.getInfo("average,meanz")).doubleValue(),0.1);
		assertEquals(5,((Long) cGA.getInfo("groups")).longValue());

		// check the output file itself
		int[] rowCols=checkFile(tempFilePath,false);
		assertEquals(5,rowCols[0]);
		assertEquals(40,rowCols[1]);

	}


	/**
	 * Test method for {@link tipl.tools.GrayAnalysis#StartLacunaAnalysis}.
	 */
	@Test
	public void testAddDensityColumn() {
		System.out.println("Testing execute");
		final TImgRO testImg = TestPosFunctions.wrapIt(10,
				new TestPosFunctions.SheetImageFunction());
		final TImgRO labelImage = makeCL(testImg);
		doLacunaAnalysis(labelImage,tempFilePath);
		ITIPLPlugin cGA = GrayAnalysis.AddDensityColumn(labelImage, tempFilePath, tempFilePath2, "DENS_TEST");
		assertEquals(5,((Long) cGA.getInfo("groups")).longValue());

		// check the output file itself
		int[] rowCols=checkFile(tempFilePath2,true);
		assertEquals(5,rowCols[0]);
		assertEquals(44,rowCols[1]);

	}
	static protected String tempFilePathCSV="";
	static protected String tempFilePath="";
	static protected String tempFilePath2="";
	static final protected String[] tempFiles() {
		return new String[] {tempFilePathCSV, tempFilePath,tempFilePath2};
	}

	@BeforeClass
	public static void createTempFiles() {
		File temp;
		try {
			temp = File.createTempFile("csvTester", ".csv");
			tempFilePathCSV=temp.getAbsolutePath();
			temp = File.createTempFile("lacunaAnalysis", ".csv");
			tempFilePath=temp.getAbsolutePath();
			temp = File.createTempFile("lacunaAnalysis2", ".csv");
			tempFilePath2=temp.getAbsolutePath();
		} catch (IOException e) {
			throw new IllegalArgumentException("cannot create a temporary file for GrayAnalysis to write to");
		}
	}


	private static boolean doDelete=true;
	/**
	 * Clean up the temporary files
	 */
	@AfterClass
	public static void deleteTempFiles() {
		for (String cFile: tempFiles()) 
		{
			if (doDelete) TIPLGlobal.DeleteFile(cFile);
			else System.out.println("Want to delete:: "+cFile);
		}


	}




}