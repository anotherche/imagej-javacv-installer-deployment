package javacv_install;

import ij.IJ;
import ij.plugin.PlugIn;
import ij.gui.GenericDialog;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;





public class ImageJ_JavaCV_Installer implements PlugIn {
	
	
	//Installation parameters
	/** Base URL to the bytedeco maven repository */
	private static final String BYTEDECO_BASE_URL =
		"https://repo1.maven.org/maven2/org/bytedeco/";

	/** File suffix for the 32-bit windows */
	private static final String WIN_32 = "-windows-x86.jar";

	/** File suffix for the 64-bit windows */
	private static final String WIN_64 = "-windows-x86_64.jar";

	/** File suffix for the 32-bit linux */
	private static final String LIN_32 = "-linux-x86.jar";

	/** File suffix for the 64-bit linux */
	private static final String LIN_64 = "-linux-x86_64.jar";

	/** File suffix for the mac osx */
	private static final String MAC    = "-macosx-x86_64.jar";
	
	private static final Map<String,JavaCvComponent> OptionalComponents = new LinkedHashMap<String,JavaCvComponent>();
	private static final Map<String,JavaCvComponent> MandatoryComponents = new LinkedHashMap<String,JavaCvComponent>();
	
	public static boolean restartRequired = false;
	
	static {
		final JavaCvComponent.Specs MANDATORY = JavaCvComponent.Specs.MANDATORY;
		final JavaCvComponent.Specs HAS_PLATFORM = JavaCvComponent.Specs.HAS_PLATFORM;
		final JavaCvComponent.Specs HAS_NATLIBS = JavaCvComponent.Specs.HAS_NATLIBS;
		
		/** Fills JavaCV components collection map */ 
		//Mandatory components
		MandatoryComponents.put("javacv", new JavaCvComponent("javacv", "1.5.3", EnumSet.of(MANDATORY, HAS_PLATFORM)));
		MandatoryComponents.put("javacpp", new JavaCvComponent("javacpp", "1.5.3", EnumSet.of(MANDATORY, HAS_PLATFORM, HAS_NATLIBS)));
		
		//Optional components
		OptionalComponents.put("ffmpeg", new JavaCvComponent("ffmpeg", "4.2.2-1.5.3", EnumSet.of(HAS_PLATFORM, HAS_NATLIBS)));
		OptionalComponents.put("opencv", new JavaCvComponent("opencv", "4.3.0-1.5.3", EnumSet.of(HAS_PLATFORM, HAS_NATLIBS)));
		OptionalComponents.put("openblas", new JavaCvComponent("openblas", "0.3.9-1.5.3", EnumSet.of(HAS_PLATFORM, HAS_NATLIBS)));
	}
	
	
	public static void main(String[] args) {
		if(CheckJavaCV(null, true, false)){
			IJ.log("javacv is installed");
		}
			
		else
			IJ.log("javacv install failed or canceled");
			
	}

	@Override
	public void run(String arg) {
		if(CheckJavaCV(null, true, false)) {
			IJ.log("javacv is installed");
		}
		else
			IJ.log("javacv install failed or canceled");
	}
	
	static class JavaCvComponent {
		private String name;
		private String version;
		private EnumSet<Specs> CompSpecs;
		
		public JavaCvComponent(String name, String version, boolean isMandatory, boolean hasPlatform, boolean hasNativeLibs){
			this.name = name;
			this.version = version;
			CompSpecs = EnumSet.noneOf(Specs.class);
			if (isMandatory) CompSpecs.add(Specs.MANDATORY);
			if (hasPlatform) CompSpecs.add(Specs.HAS_PLATFORM);
			if (hasNativeLibs) CompSpecs.add(Specs.HAS_NATLIBS);
			
		}
		
		public JavaCvComponent(String name, String version, EnumSet<Specs> compSpecs){
			this.name = name;
			this.version = version;
			CompSpecs = compSpecs.clone();
			
		}
		
		/**
		 * Construct a list of dependencies corresponding
		 * to the JavaCV component.
		 */
		public ArrayList<Dependency> GetDepsList() throws Exception {
			ArrayList<Dependency> deps = new ArrayList<Dependency>();
			String depsPath = GetDependenciesPath(), 
			natLibsPath = depsPath + (IJ.isLinux() ? (IJ.is64Bit() ? "linux64" : "linux32") 
					 : (IJ.isWindows() ? (IJ.is64Bit() ? "win64" : "win32") : "macosx"))
					+File.separator;
			
			String platformSuffix = null;
			
			if(IJ.isLinux())
				platformSuffix = IJ.is64Bit() ? LIN_64 : LIN_32;
			else if(IJ.isWindows())
				platformSuffix = IJ.is64Bit() ? WIN_64 : WIN_32;
			else if(IJ.isMacOSX())
				platformSuffix = MAC;
			
			if (platformSuffix == null) throw new Exception("The operating system is not supported by the JavaCV installation plugin");

			String filename, url;
			filename = name+"-"+version+".jar";
			url = BYTEDECO_BASE_URL+name+"/"+version+"/"+filename;
			deps.add(new Dependency(filename, depsPath, url));
			if(HasPlatform()){
				filename = name+"-platform-"+version+".jar";
				url = BYTEDECO_BASE_URL+name+"-platform/"+version+"/"+filename;
				deps.add(new Dependency(filename, depsPath, url));
			}
			if(HasNativeLibs()){
				filename = name+"-"+version+platformSuffix;
				url = BYTEDECO_BASE_URL+name+"/"+version+"/"+filename;
				deps.add(new Dependency(filename, natLibsPath, url));
			}
			return deps;
		}
		
		public String getName(){
			return name;
		}
		public String getVersion(){
			return version;
		}
		public boolean IsMandatory(){
			return CompSpecs.contains(Specs.MANDATORY);
		}
		public boolean HasPlatform(){
			return CompSpecs.contains(Specs.HAS_PLATFORM);
		}
		public boolean HasNativeLibs(){
			return CompSpecs.contains(Specs.HAS_NATLIBS);
		}
		
		public static enum Specs {
	        MANDATORY,
	        HAS_PLATFORM,
	        HAS_NATLIBS
	    }
	}
	
			
	
	
	static class Dependency {
		private String depFilename;
		private String depDirectory;
		private String depURL;
		
		public Dependency(String filename, String directory, String url) {
			this.depFilename = filename;
			this.depDirectory = directory;
			this.depURL = url;
			
		}
		
		public boolean isInstalled() {
			return (new File(depDirectory+depFilename)).exists();
		}
		
		/**
		 * Download and install a JavaCV component specified by the dependency 
		*/
		public boolean Install() throws Exception {
			boolean success = false;
			
			File directory = new File(depDirectory);
			if(!directory.exists() && !directory.mkdirs()) {
				IJ.log("Can't create folder "+depDirectory);
				IJ.showMessage("JavaCV installation", "Can't create folder\n"+depDirectory);
				return success;
			}
			if(!directory.canWrite()) {
				IJ.log("No permissions to write to folder "+depDirectory);
				IJ.showMessage("JavaCV installation", "No permissions to write to folder\n"+depDirectory);
				return success;
			}
			
			IJ.log("downloading " + depURL);
			InputStream is = null;
			URL url = null;
			try {
				url = new URL(depURL);
				URLConnection conn = url.openConnection();
				is = conn.getInputStream();
			} catch(MalformedURLException e1) {
				throw new Exception(depURL + " is not a valid URL");
			} catch(IOException e1) {
				throw new Exception("Can't open connection to " + depURL);
			}
			byte[] content = readFully(is);
			File out = new File(depDirectory, new File(url.getFile()).getName());
			IJ.log(" to " + out.getAbsolutePath());
			FileOutputStream fos = null;
			try {
				fos = new FileOutputStream(out);
				fos.write(content);
				fos.close();
				success = true;
			} catch(FileNotFoundException e1) {
				throw new Exception("Could not open "
					+ out.getAbsolutePath() + " for writing. "
					+ "Maybe not enough permissions?");
			} catch(IOException e2) {
				throw new Exception("Error writing to "
					+ out.getAbsolutePath());
			}
			return success;
		}
	}
	
	private static ArrayList<Dependency> dependencies;
	
	
	private static String GetDependenciesPath(){
		char altSeparator = '/'== File.separatorChar?'\\':'/';
		String appPath = IJ.getDirectory("imagej").replace(altSeparator, File.separatorChar);
		String jarsPath = appPath+"jars"+ File.separatorChar;
		boolean fiji = false;
		ClassLoader cl = ClassLoader.getSystemClassLoader();
		URL[] urls = ((java.net.URLClassLoader) cl).getURLs();
		for (URL url: urls) 
			if (url.getFile().replace(altSeparator, File.separatorChar).contains(jarsPath)) {
				fiji = true;
				break;
			}
		
		if (!fiji) {
		cl = IJ.getClassLoader();
		urls = ((java.net.URLClassLoader) cl).getURLs();
		for (URL url: urls) 
			if (url.getFile().replace(altSeparator, File.separatorChar).contains(jarsPath)) {
				fiji = true;
				break;
			}
		}
		
		
		if (fiji) return jarsPath;
		else return  IJ.getDirectory("plugins");

	}
	
	
	
	/**
	 * Returns true if video import plugin can run.
	 * Checks if all necessary dependencies are installed, 
	 * prompts to install if missing.
	 */
	public static boolean CheckJavaCV(String depsNames, boolean showOptDlg, boolean forceReinstall){
	
		
		if(!IJ.isLinux() && !IJ.isWindows() && !IJ.isMacOSX()) {
			IJ.showMessage("JavaCV installation", "Unsupported operating system");
			return false;
		}
		
		
		ArrayList<String> optionalCompList = new ArrayList<>(OptionalComponents.keySet());
		
		String[] optionalCompNames = new String[optionalCompList.size()];
		optionalCompNames = optionalCompList.toArray(optionalCompNames);    
	    boolean[] compSelection = new boolean[optionalCompList.size()];
	    if (depsNames != null) {
	    	String[] deps = depsNames.split("[ ]+");
	    	if (deps.length > 0){
	    		for(String dep : deps){
	    			int compIndex = optionalCompList.indexOf(dep);
	    			if (compIndex>-1) compSelection[compIndex] = true;
	    		}
	    	}
	    }
		
	    if (showOptDlg){
			GenericDialog gd = new GenericDialog("JavaCV installation options");
			String[] Options = new String[]{"Install missing", "Force reinstall"};
			gd.addRadioButtonGroup("Select installation option", Options, 2, 1, forceReinstall?Options[1]:Options[0]);
			gd.addMessage("Select necessary packages");
			
			gd.addCheckboxGroup(5, 5, optionalCompNames, compSelection);
			gd.pack();
			gd.showDialog();
			if (gd.wasCanceled()) return false;
			if (gd.getNextRadioButton().equals(Options[1])) forceReinstall = true;
			for (int i=0; i<compSelection.length; i++) compSelection[i] = gd.getNextBoolean();
		}
		
		
		
		dependencies = new ArrayList<Dependency>();
		
		
		try {
			for (Map.Entry<String, JavaCvComponent> entry : MandatoryComponents.entrySet()) {
				JavaCvComponent comp = entry.getValue();
				dependencies.addAll(comp.GetDepsList());
			}
			
			
			for (int i = 0; i<optionalCompNames.length;i++) {
				
				if(compSelection[i]){// || !comp.IsOptional()){
					JavaCvComponent comp = OptionalComponents.get(optionalCompNames[i]);
					dependencies.addAll(comp.GetDepsList());	
				}
			}
		} catch (Exception e1) {
			
			e1.printStackTrace();
		}
		
		
		boolean installConfirmed = false, installed = true;
		for(Dependency dep : dependencies) 
			if (forceReinstall || !dep.isInstalled()) {
				if (!forceReinstall && !installConfirmed 
					&& !(installConfirmed = IJ.showMessageWithCancel(
											"JavaCV dependency check",
											"Not all required dependencies are installed.\n" +
											"Auto-install?"))) return false;
				
				try {
					if (!dep.Install()) return false;
				} catch (Exception e) {
					IJ.error(e.getMessage());
					IJ.log(e.getMessage());
					e.printStackTrace();
					installed = false;
				}
			}
			
			
			
	
			
		if (installConfirmed || forceReinstall) {
			IJ.showMessage("JavaCV installation", "Please restart ImageJ now");
			IJ.log("ImageJ restart is required after javacv installation!");
			restartRequired = true;
		} else restartRequired = false;
		return installed;	
	}
				
	
	

	/**
	 * Reads all bytes from the given InputStream and returns it as a
	 * byte array.
	 */
	public static byte[] readFully(InputStream is) throws Exception {
		ByteArrayOutputStream buf = new ByteArrayOutputStream();
		int c = 0;
		try {
			while((c = is.read()) != -1)
				buf.write(c);
			is.close();
		} catch(IOException e) {
			throw new Exception("Error reading from " + is);
		}
		return buf.toByteArray();
	}
}


