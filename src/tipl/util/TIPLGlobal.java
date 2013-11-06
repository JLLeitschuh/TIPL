package tipl.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import tipl.formats.VirtualAim;
import tipl.tools.BaseTIPLPluginIn;

public class TIPLGlobal {
	/**
	 * simple method to get an executor service, eventually allows this to be changed to another / distributed option
	 * @param numOfCores
	 * @return
	 */
	public static ExecutorService requestSimpleES(int numOfCores) {
		return Executors.newFixedThreadPool(Math.min(TIPLGlobal.availableCores,numOfCores),TIPLGlobal.daemonFactory);
	}
	public static ExecutorService requestSimpleES() { return requestSimpleES(TIPLGlobal.availableCores);}
	
	public static ArgumentParser activeParser(String[] args) {return activeParser(new ArgumentParser(args));}
	/**
	 * parser which actively changes local, maxcores, maxiothread and other TIPL wide parameters
	 * @param sp input argumentparser
	 * @return
	 */
	public static ArgumentParser activeParser(ArgumentParser sp) {
		VirtualAim.scratchDirectory = sp.getOptionString("@localdir",
				VirtualAim.scratchDirectory, "Directory to save local data to");
		VirtualAim.scratchLoading=sp.getOptionBoolean("@local", VirtualAim.scratchLoading,"Load image data from local filesystems");
		TIPLGlobal.availableCores = sp.getOptionInt("@maxcores",
				TIPLGlobal.availableCores,
				"Number of cores/threads to use for processing");

		TIPLGlobal.supportedIOThreads = sp.getOptionInt("@maxiothread",
				TIPLGlobal.supportedIOThreads,
				"Number of cores/threads to use for read/write operations");
		if (sp.hasOption("?")) System.out.println(sp.getHelp());		
		return sp.subArguments("@");
	}
	
	
	/**
	 * shutdown an executor service and wait for everything to finish.
	 * @param inPool
	 */
	public static void waitForever(ExecutorService inPool) {
		inPool.shutdown();
		try {
			inPool.awaitTermination(100, TimeUnit.DAYS);
		} catch(Exception e) {
			e.printStackTrace();
			throw new IllegalArgumentException(inPool+" executorservice crashed:"+e.getMessage());
		}
	}
	/**
	 * the current runtime being used (whatever that is)
	 */
	public static Runtime curRuntime = Runtime.getRuntime();
	/** number of cores available for processing tasks */
	public static int availableCores = curRuntime.availableProcessors();
	/** maximum number of IO operations to perform at the same time */
	public static int supportedIOThreads = 4;
	/**
	 * so the threads do not need to manually be shutdown
	 * 
	 */
	public static final ThreadFactory daemonFactory = new ThreadFactory() {
		@Override
		public Thread newThread(final Runnable runnable) {
			final Thread thread = Executors.defaultThreadFactory().newThread(
					runnable);
			thread.setDaemon(true);
			return thread;
		}
	};
	final static boolean useApacheForCopy=true;
	/** a simple file copy function for managing outputs */
	public static void copyFile(final File sourceFile, final File destFile)
			throws IOException {

		if (!destFile.exists()) {
			destFile.createNewFile();
		}
		if (useApacheForCopy) {
			org.apache.commons.io.FileUtils.copyFile(sourceFile, destFile); // since standard java 1.6 does not support 2g+ files
			return;
		} else {
			FileChannel source = null;
			FileChannel destination = null;
			try {
				source = new FileInputStream(sourceFile).getChannel();
				destination = new FileOutputStream(destFile).getChannel();
				destination.transferFrom(source, 0, source.size());
			} finally {
				if (source != null) {
					source.close();
				}
				if (destination != null) {
					destination.close();
				}
			}
		}

	}


	public static void copyFile(final String sourceFile, final String destFile) {
		try {
			copyFile(new File(sourceFile), new File(destFile));
		} catch (final Exception e) {
			e.printStackTrace();
			System.out.println("Copy file failed (disk full?) " + sourceFile
					+ ", " + destFile);
			System.gc();
		}
	}

	public static boolean DeleteFile(final String file) {
		return DeleteFile(file, "Unk");
	}

	/** Delete files */
	public static boolean DeleteFile(final String file, final String whoDel) {
		final File f1 = new File(file);
		final boolean success = f1.delete();
		if (!success) {
			System.out.println(whoDel + "\t" + "ERROR:" + file
					+ " could not be deleted.");
			return false;
		} else {
			System.out.println(whoDel + "\t" + file + " successfully deleted.");
			return true;
		}
	}

	/** Utility Function Section */
	/**
	 * A function to register the current filename as a temporary file that
	 * should be delated when the runtime finishes
	 **/
	public static void DeleteTempAtFinish(final String delName) {
		curRuntime.addShutdownHook(new Thread() {
			public boolean MyDeleteFile(final String file, final String whoDel) {
				final File f1 = new File(file);
				final boolean success = f1.delete();
				if (!success) {
					System.out.println(whoDel + "\t" + "ERROR:" + file
							+ " could not be deleted.");
					return false;
				} else {
					System.out.println(whoDel + "\t" + file
							+ " successfully deleted.");
					return true;
				}
			}

			@Override
			public void run() {
				System.out
				.println("SHUTHOOK\tChecking to ensure that all temp-files have been deleted");
				MyDeleteFile(delName, "SHUTHOOK");
			}
		});
	}

	/** get executor for IO operations */
	public static ExecutorService getIOExecutor() {
		return Executors.newFixedThreadPool(IOThreads(), daemonFactory);
	}

	/** get executor service for tasks */
	public static ExecutorService getTaskExecutor() {
		return Executors.newFixedThreadPool(availableCores, daemonFactory);
	}

	/** actual number of IO threads to use */
	public static int IOThreads() {
		return BaseTIPLPluginIn.min(supportedIOThreads, availableCores);
	}

	/**
	 * reserve cores for an operation that other threads can then no longer user
	 */
	public static synchronized int reserveCores(final int desiredCores) {
		final int givenCores = (availableCores > desiredCores) ? desiredCores
				: availableCores;
		availableCores -= givenCores;
		return givenCores;
	}

	/** return the cores when a computation is complete */
	public static synchronized void returnCores(final int finishedCores) {
		availableCores += finishedCores;
	}

	/** Function to try and open an aim file, return true if it is successful */
	public static boolean tryOpen(final String filename) {

		VirtualAim tempAim = null;
		if (filename.length() > 0) {
			System.out.println("Trying to open ... " + filename);
		} else {
			System.out
			.println("Filename is empty, assuming that it is not essential and proceeding carefully!! ... ");
			return true;
		}

		try {
			tempAim = new VirtualAim(filename);
			return (tempAim.ischGuet);
		} catch (final Exception e) {
			tempAim = null;
			System.gc();
			return false;
		}

	}

}
