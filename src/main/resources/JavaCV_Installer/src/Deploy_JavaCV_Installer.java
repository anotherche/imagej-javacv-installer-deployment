/*
 * Copyright (C) 2018-2021 Stanislav Chizhik
 * ImageJ/Fiji plugin which helps to download and to install components of javacv package 
 * (java interface to OpenCV, FFmpeg and other) by Samuel Audet.
 * Other plugins which require javacv may use it to check if necessary libraries are 
 * installed and to install missing components.
 */

package javacv_install_deployment;

import ij.IJ;
import ij.ImageJ;
import ij.Menus;
import ij.Prefs;
import ij.plugin.PlugIn;
import ij.plugin.filter.PlugInFilter;
import ij.plugin.filter.PlugInFilterRunner;
import ij.plugin.frame.Editor;
import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.*;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLConnection;
import java.util.jar.*;
import java.util.stream.*;
import javax.tools.*;
import java.util.*;
import java.awt.Font;






public class Deploy_JavaCV_Installer implements PlugIn {
	
	
	/**Installation parameters*/
	private static final String deployVersion = "0.1.0";
	private static final String reqInstallerVersion = "0.2.3";
	private static final String installerClassName = "JavaCV_Installer";
	private static final String installerSrcName = installerClassName+".java";
	private static final String installerPkg = "javacv_install";
	private static final String msgTitle = "JavaCV Installer deployment"; 
	
//	/**Installer reflection method*/ 
//	private static Method mIsRestartRequired;
	
	/** Base URL to the maven repository */
	private static final String MAVEN_BASE_URL = "https://repo1.maven.org/maven2/";
		
	private static final String APACHEMAVEN = "org/apache/maven/";
	
	private static ArrayList<Artifact> artifacts;
	private static String installerDirectory;
	private static String deployDirectory;
	private static String deployJarPath;
	
	private boolean isJarDeploy;
	private static boolean restartRequired = false;
	
	static {
		artifacts = new ArrayList<Artifact>();
		
		artifacts.add(new Artifact("maven-artifact", "3.6.3", APACHEMAVEN + "maven-artifact/"));
		artifacts.add(new Artifact("maven-builder-support", "3.6.3", APACHEMAVEN + "maven-builder-support/"));
		artifacts.add(new Artifact("maven-model", "3.6.3", APACHEMAVEN + "maven-model/"));
		artifacts.add(new Artifact("maven-model-builder", "3.6.3", APACHEMAVEN + "maven-model-builder/"));
		artifacts.add(new Artifact("maven-repository-metadata", "3.6.3", APACHEMAVEN + "maven-repository-metadata/"));
		artifacts.add(new Artifact("maven-resolver-provider", "3.6.3", APACHEMAVEN + "maven-resolver-provider/"));
		artifacts.add(new Artifact("maven-resolver-api", "1.6.1", APACHEMAVEN + "resolver/maven-resolver-api/"));
		artifacts.add(new Artifact("maven-resolver-connector-basic", "1.6.1", APACHEMAVEN + "resolver/maven-resolver-connector-basic/"));
		artifacts.add(new Artifact("maven-resolver-impl", "1.4.1", APACHEMAVEN + "resolver/maven-resolver-impl/"));
		artifacts.add(new Artifact("maven-resolver-spi", "1.6.1", APACHEMAVEN + "resolver/maven-resolver-spi/"));
		artifacts.add(new Artifact("maven-resolver-transport-file", "1.1.0", APACHEMAVEN + "resolver/maven-resolver-transport-file/"));
		artifacts.add(new Artifact("maven-resolver-transport-http", "1.1.0", APACHEMAVEN + "resolver/maven-resolver-transport-http/"));
		artifacts.add(new Artifact("maven-resolver-util", "1.6.1", APACHEMAVEN + "resolver/maven-resolver-util/"));
		artifacts.add(new Artifact("org.eclipse.sisu.inject", "0.3.4", "org/eclipse/sisu/org.eclipse.sisu.inject/"));
		artifacts.add(new Artifact("plexus-interpolation", "1.25", "org/codehaus/plexus/plexus-interpolation/"));
		artifacts.add(new Artifact("plexus-utils", "3.2.1", "org/codehaus/plexus/plexus-utils/"));
		artifacts.add(new Artifact("slf4j-api", "1.7.30", "org/slf4j/slf4j-api/"));
		artifacts.add(new Artifact("jcl-over-slf4j", "1.7.30", "org/slf4j/jcl-over-slf4j/"));
		artifacts.add(new Artifact("javax.inject", "1", "javax/inject/javax.inject/"));
		artifacts.add(new Artifact("httpcore", "4.4.13", "org/apache/httpcomponents/httpcore/"));
		artifacts.add(new Artifact("httpclient", "4.5.12",  "org/apache/httpcomponents/httpclient/"));
		artifacts.add(new Artifact("commons-lang3", "3.10", "org/apache/commons/commons-lang3/"));
		artifacts.add(new Artifact("commons-codec", "1.14", "commons-codec/commons-codec/"));
		artifacts.add(new Artifact("commons-logging", "1.2", "commons-logging/commons-logging/"));
		
		/**where to deploy the Installer*/
		installerDirectory = IJ.getDirectory("plugins")+installerClassName+File.separatorChar;
		
//		restartRequired = Prefs.get("javacv.deploy_restart", false);
//		Prefs.set("javacv.deploy_restart", false);
		
	}
	
	
	// public static void main(String[] args) {
		// if(CheckDependencies(false, false)){
			// IJ.log("javacv installer dependencies are installed");
			
		// }
			
		// else
			// IJ.log("installation of dependencies failed or canceled");
			
	// }

	@Override
	public void run(String arg)  {
		
		if(!IJ.isLinux() && !IJ.isWindows() && !IJ.isMacOSX()) {
			IJ.showMessage(msgTitle, "Unsupported operating system");
			return;
		}
		
				
		IJ.register(this.getClass());
		
		
		
		boolean canWriteToImageJ = new File(IJ.getDirectory("imagej")).canWrite();
		
		if (!canWriteToImageJ) {
            String path = IJ.getDirectory("imagej");
            String msg = "No write access: "+path;
            if (IJ.isMacOSX() && path!=null && path.startsWith("/private/var/folders/")) {
                msg = "ImageJ is in a read-only folder due to Path Randomization.\n"
                + "To work around this problem, drag ImageJ.app to another\n"
                + "folder and then (optionally) drag it back.";
            }
            IJ.error("JavaCV Installer", msg);
            return;
        }
		
		
		if (restartRequired) {
			IJ.showMessage(msgTitle, "ImageJ must be restarted upon the deployment!");
			return;
		}
		
		try {
			if (filteredFileList(IJ.getDirectory("imagej")+"update"+File.separatorChar, new String[]{installerClassName}, null, true).size()>0) {
				IJ.showMessage(msgTitle, "ImageJ must be restarted upon update or deployment.");
				return;
			}
		} catch (IOException e) {
			if(IJ.debugMode) { 
				IJ.log(e.getLocalizedMessage());
				IJ.log(e.toString());
			}
		}
		
		if (isRestartRequiredByInstaller()) {
			IJ.showMessage(msgTitle, "ImageJ must be restarted after previous install operation!");
			return;
		}
		
		boolean installerCheck = CheckInstaller(reqInstallerVersion);
		boolean ctrlKey = IJ.controlKeyDown();
		boolean forced = Prefs.get("javacv.forced_deploy", false);
		
		if(!ctrlKey && !forced && installerCheck) {
			IJ.showMessage(msgTitle, "JavaCV installer is already installed\n"
					+"For a forced redeployment, start \"Deploy JavaCV installer\" with the ctrl key pressed." );
			return;
		}
		
		
		
//		JavaCompiler javaxCompiler = ToolProvider.getSystemJavaCompiler();
//		Class legacyCompiler=null;
//		try {
//			legacyCompiler = Class.forName("com.sun.tools.javac.Main");
//		} catch (ClassNotFoundException e2) {
//			if(IJ.debugMode) { 
//				IJ.log(e2.getLocalizedMessage());
//				IJ.log(e2.toString());
//			}
//		}
//		
//		if(javaxCompiler==null && legacyCompiler==null) {
//				IJ.showMessage(msgTitle, "The java compiler was not found.\n"
//			+"To use this deployment, you must first download and install JDK containing the compiler.");
//				return;
//		}
//		
//		String javaxCompilerPath="";
//		String legacyCompilerPath="";
//		boolean badJavaxCompiler=false;
//		boolean badLegacyCompiler=false;
//		if (javaxCompiler!=null) {
//			javaxCompilerPath = javaxCompiler.getClass().getProtectionDomain().getCodeSource().getLocation().getPath().replace('\\','/');
//			if (javaxCompilerPath.indexOf(IJ.getDirectory("plugins").replace('\\','/'))!=-1) badJavaxCompiler=true;
//			
//		}
//		if (legacyCompiler!=null) {
//			legacyCompilerPath = legacyCompiler.getProtectionDomain().getCodeSource().getLocation().getPath().replace('\\','/');
//			if (legacyCompilerPath.indexOf(IJ.getDirectory("plugins").replace('\\','/'))!=-1) badLegacyCompiler=true;
//			
//		}
//		if(badJavaxCompiler||badLegacyCompiler) IJ.showMessage(msgTitle, "Plugins found that may prevent compilation of source code:\n"
//										+(badJavaxCompiler?"javax compiler: "+ javaxCompilerPath.substring(1)+"\n":"")
//										+(badLegacyCompiler?"legacy compiler: "+ legacyCompilerPath.substring(1) +"\n":"")
//										+"In case of compilation errors, we recommend temporarily isolating these files.");
		
		JavaCompiler javaxCompiler = ToolProvider.getSystemJavaCompiler();
		Class legacyCompiler=null;
		try {
			legacyCompiler = Class.forName("com.sun.tools.javac.Main");
		} catch (ClassNotFoundException e2) {
			if(IJ.debugMode) { 
				IJ.log(e2.getLocalizedMessage());
				IJ.log(e2.toString());
			}
		}
		
		if(javaxCompiler==null && legacyCompiler==null) {
				IJ.showMessage(msgTitle, "The java compiler was not found.\n"
			+"To use this deployment, you must first download and install JDK containing the compiler.\n\n"
			+"Hint: current fresh distribution of ImageJ 1.53 contains\n"
			+"necessary library tools.jar in the included jre 1.8.0_172.");
				return;
		}
		
		String javaxCompilerPath="";
		String legacyCompilerPath="";
		boolean badJavaxCompiler=false;
		boolean badLegacyCompiler=false;
		if (javaxCompiler!=null) {
			javaxCompilerPath = javaxCompiler.getClass().getProtectionDomain().getCodeSource().getLocation().getPath().replace('\\','/');
			if (javaxCompilerPath.indexOf(IJ.getDirectory("plugins").replace('\\','/'))!=-1) badJavaxCompiler=true;
			
		}
		if (legacyCompiler!=null) {
			legacyCompilerPath = legacyCompiler.getProtectionDomain().getCodeSource().getLocation().getPath().replace('\\','/');
			if (legacyCompilerPath.indexOf(IJ.getDirectory("plugins").replace('\\','/'))!=-1) badLegacyCompiler=true;
			
		}
		if(badJavaxCompiler||badLegacyCompiler) IJ.showMessage(msgTitle, "Plugins found that may prevent compilation of source code:\n"
										+(badJavaxCompiler?"javax compiler: "+ javaxCompilerPath.substring(1)+"\n":"")
										+(badLegacyCompiler?"legacy compiler: "+ legacyCompilerPath.substring(1) +"\n":"")
										+"In case of compilation errors, we recommend temporarily isolating these files.");
		
		
		
//		IJ.register(this.getClass());
		
//		if (restartRequired) {
//			IJ.showMessage(msgTitle, "ImageJ must be restarted after JavaCV Installer deployment!");
//			return;
//		}
//		
//		
//		
//		try {
//			if (filteredFileList(IJ.getDirectory("imagej")+"update"+File.separatorChar, new String[]{installerClassName}, null, true).size()>0) {
//				IJ.showMessage(msgTitle, "ImageJ must be restarted after update or deployment.");
//				return;
//			}
//		} catch (IOException e) {
//			if(IJ.debugMode) { 
//				IJ.log(e.getLocalizedMessage());
//				IJ.log(e.toString());
//			}
//		}
//		
		
		
		/** Check if the Installer is already here*/
		if (!ctrlKey && !forced && installerCheck) {
			IJ.log("JavaCV Installer already installed with version not older than required ("+reqInstallerVersion+")");
			restartRequired = false;
			Prefs.set("javacv.deploy_restart", false);
			try {
				
				if (isRestartRequiredByInstaller()) {
					IJ.showMessage(msgTitle, "ImageJ must be restarted after previuos install operation!");
					return;
				}
				
				IJ.run("Install JavaCV libraries", "");
			} catch (Throwable e) {
				if(IJ.debugMode) { 
					IJ.log(e.getLocalizedMessage());
					IJ.log(e.toString());
				}
			}
			return;
		} else {
			if (!installerCheck) IJ.log("JavaCV Installer not found or unsupported version. Starting the installer deployment...");
			else IJ.log("JavaCV Installer will be forcibly redeployed...");
			IJ.log("=======================================");
		}
		
		
		
		getDeployInfo(); /**collect important info like deploy directory, deploy method (.jar or compile), deploy.jar path*/
		
		/**where to deploy the Installer*/
//		installerDirectory = IJ.getDirectory("plugins")+installerClassName+File.separatorChar;
		if (!CheckCreateDirectory(installerDirectory)) {
			restartRequired = false;
			Prefs.set("javacv.deploy_restart", false);
			return; //we cannot create installer directory
		}
		
		String installerResourceDir = installerDirectory+"installer_resource"+File.separatorChar;
		if (!CheckCreateDirectory(installerResourceDir)) {
			restartRequired = false;
			Prefs.set("javacv.deploy_restart", false);
			return; //we cannot directory for extracted resource
		}
		
		Path deployPath = Paths.get(deployDirectory).normalize();
		Path installerPath = Paths.get(installerDirectory).normalize();
				
		boolean dependenciesOK = CheckDependencies(false, false);
		if(restartRequired) {
			IJ.log("To proceed with JavaCV installer deployment,"
					+"you need to restart ImageJ and start \"Deploy JavaCV installer\" again.");
			IJ.showMessage(msgTitle, "To proceed with JavaCV installer deployment,\n"
						+"you need to restart ImageJ and start \"Deploy JavaCV installer\" again.");
			Prefs.set("javacv.forced_deploy", ctrlKey);
			return;
		}
		
		if(dependenciesOK) {//check if dependencies are present for the installer compilation and download if necessary
			
			
			
			/**if we deploy from .jar we need extract source of the Installer from the .jar to the deploy directory*/
			if (isJarDeploy) {
				/**extract installer source*/ 
				try {
					JarInputStream deployJar = new JarInputStream(new FileInputStream(deployJarPath));

					jarExtractPars[] extracts = new jarExtractPars[]{
							new jarExtractPars(installerSrcName, deployDirectory, installerSrcName)
							//new jarExtractPars(resourceName, installerResourceDir, "plugins.config")
					};
					extractFromJar(extracts, deployJar);
					
				} catch (IOException e) {
					IJ.log("ERROR: Cannot extract Installer source from jar.");
					IJ.log(e.toString());
					restartRequired = false;
					Prefs.set("javacv.deploy_restart", false);
					return;
				}
			}
			
			/**Installer source must be in the deploy dir (extracted from .jar or directly copied)*/
			if (!(new File(deployDirectory + installerSrcName).exists())) {
				IJ.showMessage(msgTitle, installerSrcName + " is not found in\n"+deployDirectory);
				restartRequired = false;
				Prefs.set("javacv.deploy_restart", false);
				return;
			}
					
			
			
			if (!deployPath.equals(installerPath)) {
				try {
					Files.copy(Paths.get(deployDirectory+installerSrcName), Paths.get(installerDirectory+installerSrcName),StandardCopyOption.REPLACE_EXISTING);
				} catch (IOException e) {
					IJ.log(e.toString());
					IJ.log("ERROR: Cannot copy Installer source to the destination directory "+deployDirectory);
					try {
						Files.delete(Paths.get(deployDirectory+installerSrcName));
					} catch (IOException e1) {
						// TODO Auto-generated catch block
						e1.printStackTrace();
					}
					restartRequired = false;
					Prefs.set("javacv.deploy_restart", false);
					return;
				}
			}
			

			
			
			IJ.log("javacv installer dependencies are installed");
			IJ.log("compilling javacv installer...");
			IJ.log("=======================================");
			IJ.run("Compiler...", "target=1.8");
			CompilerMod compiler = new CompilerMod();
			String srcPath = installerDirectory+installerSrcName;
			String bldPath = installerDirectory.substring(0, installerDirectory.length()-1);
			String[] args = new String[]{"-d", bldPath};
			String[] sources = new String[]{srcPath};
			if (!compiler.compile(sources, args)){
				IJ.log("ERROR: Installer not compiled.");
				try {
					Files.delete(Paths.get(installerDirectory+installerSrcName));
				} catch (IOException e1) {
					e1.printStackTrace();
				}
				restartRequired = false;
				Prefs.set("javacv.deploy_restart", false);
				return;
			}
			
			//compiler.runPlugin(installerPkg+"."+installerSrcName, installerDirectory+installerPkg, arg, false);
			if(new File(installerDirectory+"installcfg.xml").exists())
				try {
					Files.delete(Paths.get(installerDirectory+"installcfg.xml"));
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			compiler.runPlugin(installerPkg+"."+installerClassName, bldPath, "", false);
		} else {
			IJ.log("ERROR: Installation of dependencies failed or canceled. Cannot build Installer.");
			restartRequired = false;
			Prefs.set("javacv.deploy_restart", false);
			Prefs.set("javacv.forced_deploy", false);
			return;
		}
		
		
		/**jar files creation*/
		try {
			Manifest manifest = new Manifest();
			manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
			
			
			/**now we want to build installer.jar
			/*create plugin.config if there is no such
			*/
			String installerResourcePath = installerResourceDir + "installer_plugins.config";
			try (PrintWriter out = new PrintWriter(installerResourcePath)){
			out.println("# Name: ImageJ_JavaCV_Installer");
			out.println("# Author: Stanislav Chizhik");
			out.println("# Version: 0.2.3");
			out.println("# Date: 2021/09/20");
			out.println("# Requires: ImageJ 1.53");
			out.println("");
			out.println("# ImageJ plugin which helps to download and to install components of javacv package"
					+" (java interface to OpenCV, FFmpeg and other) by Samuel Audet.");
			out.println("# Other plugins which require javacv may use it to check if necessary libraries are installed and to install missing components.");
			out.println("");
			out.println("Plugins>JavaCV>, \"Install JavaCV libraries\","+installerPkg+"."+installerClassName+"(\"\")");
			out.close();
			} catch (FileNotFoundException e) {
				IJ.log("WARNING: plugins.config not generated. Installer plugin may become not visible for user.");
				IJ.log(e.toString());
			}
			
			/**build installer.jar
			 * solve conflicts and suggest non-conflicting file path
			 */
			String installerJarPath = SolveJarConflicts(installerDirectory, installerClassName, reqInstallerVersion);
			JarOutputStream installerTarget = new JarOutputStream(new FileOutputStream(installerJarPath), manifest);
			
			/**collect class files*/
			Set<String> installerClassFiles =filteredFileList(installerDirectory+installerPkg+File.separator, 
																new String[]{installerClassName}, 
																new String[]{".class"}, false);
			/**adding class files*/
			for (String file : installerClassFiles) { 
				addToJar(new File(file), installerPkg+"/", null, installerTarget);
				RemoveFile(file);
			}
			
			/**adding plugin.config*/
			addToJar(new File(installerResourcePath), "", "plugins.config", installerTarget);
			installerTarget.close();
			
			
			/**if deploy was run through compile&run we need to build deploy.jar*/
			if (!isJarDeploy) {
				
				/**if deploy is launched by compile&run we need plugin.config to build deploy.jar later*/ 
				String deployResourcePath = deployDirectory + "deploy_plugins.config";
				try (PrintWriter out = new PrintWriter(deployResourcePath)) {
				out.println("# Name: ImageJ_JavaCV_Installer_Deployment");
				out.println("# Author: Stanislav Chizhik");
				out.println("# Version: 0.1.0");
				out.println("# Date: 2021/09/20");
				out.println("# Requires: ImageJ 1.53");
				out.println("");
				out.println("# ImageJ plugin which helps to deploy (compile, build jar) JavaCV Installer,"
				+ "another plugin which helps to download and to install components of javacv package"
						+" (java interface to OpenCV, FFmpeg and other) by Samuel Audet.");
				out.println("# Other plugins which require javacv may use it to check if necessary libraries are installed and to install missing components.");
				out.println("");
				//out.println("Plugins>JavaCV, \"Deploy JavaCV installer\", javacv_install_deployment.Deploy_JavaCV_Installer");
				out.println("Plugins>JavaCV, \"Deploy JavaCV installer\", Deploy_JavaCV_Installer");
				out.close();
				} catch (FileNotFoundException e) {
					IJ.log(e.toString());
				}

					/**solve conflicts and suggest non-conflicting file path*/
				String deployJarPath = SolveJarConflicts(deployDirectory, "Deploy_JavaCV_Installer", deployVersion);
				JarOutputStream deployTarget = new JarOutputStream(new FileOutputStream(deployJarPath), manifest);
				
				/**collect class files to build jar*/
				Set<String> deployClassFiles = filteredFileList(deployDirectory, 
						new String[]{"Deploy_JavaCV_Installer","CompilerMod","CompilerToolMod","PlugInExecuterMod"}, 
						new String[]{".class"}, false);
				
				/**adding class files*/
				for (String file : deployClassFiles) {
					addToJar(new File(file), "",  null, deployTarget);
					//addToJar(new File(file), "javacv_install_deployment/",  null, deployTarget);
					RemoveFile(file);
					
				}
				
				/**adding plugin.config*/
				addToJar(new File(deployResourcePath), "", "plugins.config",  deployTarget);
				if(new File(deployResourcePath).exists()) Files.delete(Paths.get(deployResourcePath));//RemoveFile(deployResourcePath);
				
				/**adding Installer source*/
				addToJar(new File(installerDirectory+installerSrcName), installerClassName+"/src/", null, deployTarget);
				deployTarget.close();
			}
			
			
			if(new File(installerDirectory+installerSrcName).exists()) Files.delete(Paths.get(installerDirectory+installerSrcName));//RemoveFile(installerDirectory+installerSrcName);
			if(new File(deployDirectory+installerSrcName).exists()) Files.delete(Paths.get(deployDirectory+installerSrcName));//if (!deployPath.equals(installerPath)) RemoveFile(deployDirectory+installerSrcName);
			
			/**delete installer resource dir recursively for cleanup*/
			Files.walk(Paths.get(installerResourceDir))
		      .sorted(Comparator.reverseOrder())
		      .map(Path::toFile)
		      .forEach(File::delete);
			
			
			
			}  catch (Exception e1) {
				IJ.log("WARNING: jar creation error "+e1.toString());
				
			}
		restartRequired = true;
		Prefs.set("javacv.deploy_restart", true);
		IJ.log("JavaCV Installer is deployed successfully. Please restart ImageJ.");
		IJ.showMessage("JavaCV installer deployment", "JavaCV Installer is deployed successfully.\nPlease restart ImageJ to complete the deployment.");
		Prefs.set("javacv.forced_deploy", false);
		return;
		
	}

	public static  String getDeployVersion(){
		return deployVersion;
	}
	
	public static boolean isRestartRequired() {
		return restartRequired;
	}
	
	private static boolean isRestartRequiredByInstaller() {
		boolean restart = false;
		try {
			//IJ.resetClassLoader();
	        ClassLoader loader = IJ.getClassLoader();
			Class<?> installerClass = loader.loadClass(installerPkg+"."+installerClassName);
			if (installerClass == null){
				return false;
			} 
			Method mIsRestartRequired = installerClass.getMethod("isRestartRequired");
			Object installer = installerClass.newInstance();
			restart = (mIsRestartRequired != null && (boolean) mIsRestartRequired.invoke(installer));
			
		} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException 
				| ClassNotFoundException | NoSuchMethodException | SecurityException | InstantiationException e) {
			if(IJ.debugMode)
				IJ.log(e.toString());
		}
		return restart;
	}

	/** Checks if directory exists, creates it if not, and checks if we can write to it*/
	private static boolean CheckCreateDirectory(String path) {
		File directory = new File(path);
		if(!directory.exists() && !directory.mkdirs()) {
			IJ.log("Can't create folder "+path);
			IJ.showMessage(msgTitle, "Can't create folder\n"+path);
			return false;
		}
		if(!directory.canWrite()) {
			IJ.log("No permissions to write to folder "+path);
			IJ.showMessage(msgTitle, "No permissions to write to folder\n"+path);
			return false;
		}
		return true;
	}
	
	/** Checks if the Installer is installed and obtains basic installastion information */
	private static boolean CheckInstaller(String version)
	{
		Class<?> installerClass = null;
		try {
			//IJ.resetClassLoader();
	        ClassLoader loader = IJ.getClassLoader();
	        installerClass = loader.loadClass(installerPkg+"."+installerClassName);
			if (installerClass == null){
				return false;
			} 
			Object installer = installerClass.newInstance();
			Method getInstallerVersion = installerClass.getMethod("getInstallerVersion");
			Version ver = new Version((String) getInstallerVersion.invoke(installer));
			boolean result = ver.compareTo(new Version(version))>=0;
			return result;
		} catch (Throwable e) {
			if(IJ.debugMode) 
				IJ.log(e.getLocalizedMessage()+" "+e.toString());
			return false;
		} 
	}
	
	/**checks if conflicting versions of .jar present, remove them 
	 * and suggests non-conflicting name for the newly created .jar*/
	private static String SolveJarConflicts(String directory, String fileNameBase, String version) {
		String supposedName = fileNameBase +"-"+ version+".jar";
		boolean conflict = false;
		try {
			//collect a set of all conflicting files
			//Set<String> jarFiles = filteredFileList(directory, new String[]{fileNameBase}, new String[]{".jar"}, true);
			Set<String> jarFiles = filteredFileList(IJ.getDirectory("plugins"), new String[]{fileNameBase}, new String[]{".jar"}, true);
			
			
			if(jarFiles.isEmpty()) return directory + supposedName; //no conflict found
			else {
				for(String jarFile : jarFiles) {
					RemoveFile(jarFile); //checking for the removal
					String fileName = Paths.get(jarFile).getFileName().toString();
					if (fileName.equalsIgnoreCase(supposedName)) conflict = true;
				}
				
				//suggesting new name to solve conflict
				if (conflict) {
					ArrayList<String> stdMods = new ArrayList<String>();
					//looking for files with 3 fields in name
					for(String jarFile : jarFiles){
						String[] fields = Paths.get(jarFile).getFileName().toString().split("-");
						if (fields.length == 3) stdMods.add(fields[3].substring(0, fields[3].lastIndexOf(".jar")));
					}
					
					if (stdMods.size()==0) return directory + fileNameBase +"-"+ version+"-1.jar"; //no 3-field filename found; use standard name modifier
					else {
						int minmod=Integer.MAX_VALUE;
						int maxmod=0;
						for(String mod : stdMods) {
							try {
								int modnum = Integer.valueOf(mod);
								if (modnum > maxmod) maxmod = modnum;
								if (modnum < minmod) minmod = modnum;
							} catch (NumberFormatException e) {
								
							}
						}
						if (minmod > 1) return directory + fileNameBase +"-"+ version+"-1.jar";
						else return directory + fileNameBase + "-" + version + "-" + String.valueOf(maxmod+1) + ".jar";
					}
				}
			}
			
		} catch (IOException e) {
			// TODO Auto-generated catch block
			IJ.log(e.toString());
			return directory + supposedName; //return default name
		} catch (Exception e) {
			// TODO Auto-generated catch block
			IJ.log(e.toString());
			return directory + supposedName; //return default name
		}
		return directory + supposedName; //no conflict found
	}
	
	private void getDeployInfo() {
		
		String deployClassPath;
		try {
				//deployClassPath = new File(Deploy_JavaCV_Installer.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getPath();
				deployClassPath = new File(this.getClass().getProtectionDomain().getCodeSource().getLocation().toURI()).getPath();
				//String deployClassPath = Deploy_JavaCV_Installer.class.getProtectionDomain().getCodeSource().getLocation().getPath();//Paths.get(deployClassPath).getParent().toString();
				//this.getClass().getProtectionDomain().getCodeSource().getLocation().getPath();//Paths.get(deployClassPath).getParent().toString();
				if (deployClassPath.startsWith("/") || deployClassPath.startsWith("\\")) deployClassPath = deployClassPath.substring(1);
				if (deployClassPath.startsWith("file:")) deployClassPath = deployClassPath.substring(6);
				isJarDeploy = false;
				if (deployClassPath.endsWith(".jar")) { 
					isJarDeploy = true;
					deployJarPath = deployClassPath;
					deployClassPath = deployClassPath.substring(0, deployClassPath.lastIndexOf("Deploy_JavaCV_Installer"));
				}
				deployDirectory = deployClassPath;
				if(!deployDirectory.endsWith(File.separator)) deployDirectory+=File.separator;
			} catch (URISyntaxException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
	}
	 
	
	private static void addToJar(File source, String packagePath, String entryName, JarOutputStream target) throws IOException
	{
	  BufferedInputStream in = null;
	  try
	  {
		
		if (source.isDirectory())
		{
		 // String name = source.getPath().replace("\\", "/");
		  // if (!name.isEmpty())
		  // {
			// if (!name.endsWith("/"))
			  // name += "/";
			// JarEntry entry = new JarEntry(name);
			// entry.setTime(source.lastModified());
			// target.putNextEntry(entry);
			// target.closeEntry();
		  // }
		  // for (File nestedFile: source.listFiles())
			// add(nestedFile, target);
		  return;
		}
		
		JarEntry entry = new JarEntry(packagePath + ((entryName==null || entryName.isEmpty())?source.getName():entryName));//new JarEntry(source.getPath().replace("\\", "/"));
		entry.setTime(source.lastModified());
		target.putNextEntry(entry);
		in = new BufferedInputStream(new FileInputStream(source));
		byte[] buffer = new byte[1024];
		while (true)
		{
		  int count = in.read(buffer);
		  if (count == -1)
			break;
		  target.write(buffer, 0, count);
		}
		target.closeEntry();
		
	  }
	  finally
	  {
		if (in != null)
		  in.close();
	  }
	}
	
	static class jarExtractPars {
		String nameToExtract;
		String dstDir;
		String dstName;
		public jarExtractPars(String nameToExtract, String dstDir, String dstName)
		{
			this.nameToExtract = nameToExtract;
			this.dstDir = dstDir;
			this.dstName = dstName;
		}
	}
	
	private static void extractFromJar(jarExtractPars[] extracts, JarInputStream stream) throws IOException
	{
	    try
	    {
	
	        JarEntry entry;
	        while((entry = stream.getNextJarEntry())!=null)
	        {
	        	
	            String name = entry.getName();
	            int sepPos = name.lastIndexOf("/");
	            if (sepPos>-1 && sepPos!=name.length()-1) name = name.substring(sepPos+1);
	            //IJ.log("jar file: "+name);
	            for(jarExtractPars extract : extracts) {
	            	String nameToFind = extract.nameToExtract;
	            	if(name.equalsIgnoreCase(nameToFind)) {//if(name.indexOf(nameToFind)>-1) {
	            		String outpath = extract.dstDir+extract.dstName;//dstDirectories[i] + dstFiles[i];
	    	            FileOutputStream output = null;
	    	            try
	    	            {
	    	                output = new FileOutputStream(outpath);
	    	                int len = 0;
	    	                byte[] buffer = new byte[2048];
	    	                while ((len = stream.read(buffer)) > 0)
	    	                {
	    	                    output.write(buffer, 0, len);
	    	                }
	    	            }
	    	            finally
	    	            {
	    	                if(output!=null) output.close();
	    	            }
	            	}
	            }
	
	            
	            
	        }
	    }
	    finally
	    {
	        stream.close();
	    }
	}
	
	
	private static Set<String> filteredFileList(String dir, String[] names, String[] extensions, boolean recursive) throws IOException {
		
		if(!new File(dir).exists()) return new HashSet<String>();
		Stream<Path> stream = recursive?Files.walk(Paths.get(dir)).filter(Files::isRegularFile):
										Files.list(Paths.get(dir));
		return stream
				  .filter(file -> !Files.isDirectory(file))
				  .filter(file -> doesFileNameFit(file, names, true))
				  .filter(file -> doesFileNameFit(file, extensions, false))
				  .map(Path::toString)
				  .collect(Collectors.toSet());
	}
	
	private static boolean doesFileNameFit(Path file, String[] patterns, boolean starts) {
		if (patterns==null) return true;
		String name = file.getFileName().toString();
		boolean result = false;
		for(String patt : patterns) 
			result = result || patt == null || patt.isEmpty() || (starts?name.startsWith(patt):name.endsWith(patt));
		return result;
	}
	
	private static boolean RemoveFile(String fileToRemove) throws Exception {

			if(!(new File(fileToRemove)).exists()){
				return true;
			}
			Path path = Paths.get(fileToRemove);
			String imagejDirectory = IJ.getDirectory("imagej");
			String updateDirectory = imagejDirectory+"update"+File.separatorChar;
			String dstDirectory = updateDirectory+(path.getParent().toString()+File.separatorChar).substring(imagejDirectory.length());
			if (!CheckCreateDirectory(dstDirectory)) return false;
			
			String dstPath = dstDirectory + path.getFileName();
			try {
				(new File(dstPath)).createNewFile();
				return true;
			} catch (IOException e) {
				IJ.log(e.toString());
				IJ.log("WARNING: Cannot write update folder for cleanup: "+dstPath);
				//Prefs.set("javacv.install_result", "cannot write update folder");
				return false;
			}
		}
	
	
	static class Version implements Comparable<Version> {
	   
		public final int[] numbers;

	    public Version(String version) {
	        final String split[] = version.split("\\-")[0].split("\\.");
	        numbers = new int[split.length];
	        for (int i = 0; i < split.length; i++) {
	            numbers[i] = Integer.valueOf(split[i]);
	        }
	    }

	    @Override
	    public int compareTo(Version another) {
	        final int maxLength = Math.max(numbers.length, another.numbers.length);
	        for (int i = 0; i < maxLength; i++) {
	            final int left = i < numbers.length ? numbers[i] : 0;
	            final int right = i < another.numbers.length ? another.numbers[i] : 0;
	            if (left != right) {
	                return left < right ? -1 : 1;
	            }
	        }
	        return 0;
	    }
	    
	    @Override
	    public String toString() {
	    	String res="";
	    	for(int i=0; i<numbers.length - 1; i++) res+=numbers[i]+".";
	    	res+=numbers[numbers.length -1];
	    	return res;
	    }
	}
	
	static class Artifact {
		private String name;
		private String version;
		private String urlRelPath;
		public Artifact (String name, String version, String urlRelPath){
			this.name = name;
			this.version = version;
			this.urlRelPath = urlRelPath;
		}
		public String getName(){
			return name;
		}
		public String getVersion(){
			return version;
		}
		public String getUrlRelPath(){
			return urlRelPath;
		}
		public String getJarName(){
			return name+"-"+version+".jar";
		}
		public String getURL(){
			return MAVEN_BASE_URL + urlRelPath + version+"/" + getJarName();
		}
	}
	
		
	
	static class Dependency {
		private String depFilename;
		private String depDirectory;
		private String depURL;
		private String depName;
		private String depVersion;
		
		public String getFileName(){
			return depFilename;
		}
		
		public String getDirectory(){
			return depDirectory;
		}
		
		public String getURL(){
			return depURL;
		}
		
		public String getDependencyName(){
			return depName;
		}
		
		public Dependency(String depName, String depVersion, String directory, String url) {
			this.depName = depName;
			this.depVersion = depVersion;
			this.depFilename = depName+"-"+depVersion+".jar";
			this.depDirectory = directory;
			this.depURL = url;
			
		}
		
		public boolean isInstalled() {
			return (new File(depDirectory+depFilename)).exists();
		}
		
		/**
		 * Download and install an artifact specified by the dependency 
		*/
		public boolean Install() throws Exception {
			boolean success = false;
			if (!CheckCreateDirectory(depDirectory)) return success;
		
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
	
	
	
	
private static String GetDependenciesPath(){
		
		char altSeparator = '/'== File.separatorChar?'\\':'/';
		String appPath = IJ.getDirectory("imagej").replace(altSeparator, File.separatorChar);
		String jarsPath = appPath+"jars"+ File.separatorChar;
		boolean fiji = false;
//		boolean jarstest = false;
//		ClassLoader cl = ClassLoader.getSystemClassLoader();
//		URL[] urls = ((java.net.URLClassLoader) cl).getURLs();
//		for (URL url: urls) 
//			if (url.getFile().replace(altSeparator, File.separatorChar).contains(jarsPath)) {
//				jarstest = true;
//				break;
//			}
//		if (!jarstest) {
//		cl = IJ.getClassLoader();
//		urls = ((java.net.URLClassLoader) cl).getURLs();
//		for (URL url: urls) 
//			if (url.getFile().replace(altSeparator, File.separatorChar).contains(jarsPath)) {
//				jarstest = true;
//				break;
//			}
//		}
		
//		fiji = jarstest && (new File(appPath+"db.xml.gz").exists()) && IJ.getVersion().split("/").length>1;
		fiji = (new File(appPath+"db.xml.gz").exists()) && IJ.getVersion().split("/").length>1;
		
		if (fiji) return jarsPath;
		else return  IJ.getDirectory("plugins")+"jars"+File.separatorChar;

	}
	
	
	
	/**
	 * Returns true if all dependencies are found.
	 * Checks if all necessary dependencies are installed, 
	 * prompts to install if missing.
	 */
	private static boolean CheckDependencies(boolean confirmRequired, boolean forceReinstall){
	
		
		if(!IJ.isLinux() && !IJ.isWindows() && !IJ.isMacOSX()) {
			IJ.showMessage(msgTitle, "Unsupported operating system");
			return false;
		}
		
		ArrayList<Dependency> dependencies = new ArrayList<Dependency>();
		String installPath = GetDependenciesPath();//installerDirectory;
		
		for (int i = 0; i<artifacts.size();i++) {
			dependencies.add(new Dependency (artifacts.get(i).getName(), artifacts.get(i).getVersion(), installPath, artifacts.get(i).getURL()));
		}
				
		
		
		
		boolean installConfirmed = false, installed = true, installEvent = false;
		for(Dependency dep : dependencies) 
			if (forceReinstall || !dep.isInstalled()) {
				if (confirmRequired && !forceReinstall && !installConfirmed 
					&& !(installConfirmed = IJ.showMessageWithCancel(
											"Dependency check",
											"Not all required dependencies are installed.\n" +
											"Auto-install?"))) return false;
				
				try {
					installEvent = true;
					if (!dep.Install()) return false;
					else {
						if (checkConflict(installPath, dep)) {
							restartRequired = true;
						}
					}
				} catch (Exception e) {
					IJ.error(e.getMessage());
					IJ.log(e.getMessage());
					e.printStackTrace();
					installed = false;
				}
			}
			
			
		
			
		//if (installConfirmed || forceReinstall) {
//		if (installEvent) {
//			IJ.showMessage("JavaCV installer deployment", "Please restart ImageJ now");
//			IJ.log("ImageJ restart is required after javacv installation!");
//			restartRequired = true;
//		} else restartRequired = false;
		return installed;	
	}
		
	
	static boolean checkConflict(String path, Dependency dep) {
		boolean remove = false;
		try {
			String fileName = dep.getFileName();
			String depName = dep.getDependencyName();
			Set<String> jarFiles =filteredFileList(path, 
					new String[]{depName}, 
					new String[]{".jar"}, true);
			for (String jarFile : jarFiles) {
				if(Paths.get(jarFile).compareTo(Paths.get(path, fileName))!=0) {
					RemoveFile(jarFile);
					//IJ.log("Remove file "+jarFile+" CONFLICTING WITH "+Paths.get(path, fileName).toString());
					remove = true;
				}
			}
		} catch (Exception e) {
			if(IJ.debugMode) { 
				IJ.log(e.getLocalizedMessage());
				IJ.log(e.toString());
			}
		}
		return remove;
	}
	

	/**
	 * Reads all bytes from the given InputStream and returns it as a
	 * byte array.
	 */
	private static byte[] readFully(InputStream is) throws Exception {
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

class CompilerMod  {


	private static final int TARGET14=0, TARGET15=1, TARGET16=2,  TARGET17=3,  TARGET18=4, TARGET19=5;
    private static final String[] targets = {"1.4", "1.5", "1.6", "1.7", "1.8", "1.9"};
    private static final String TARGET_KEY = "javac.target";
    private static CompilerToolMod compilerTool;
    private static Editor errors;
    private static boolean generateDebuggingInfo;
    private static int target = (int)Prefs.get(TARGET_KEY, TARGET18);   
    
    
    void compileAndRun(String[] paths, String[] javacArgs, String pluginName, String pluginArg, boolean newthread) {
        if (!isJavac()) {
            //if (IJ.debugMode) 
        	IJ.log("Compiler: javac not found");
            return;
        }
        if (compile(paths, javacArgs)) runPlugin(pluginName, null, pluginArg, newthread);
    }
     
    boolean isJavac() {
        if (compilerTool==null)
            compilerTool=CompilerToolMod.getDefault();
        return compilerTool!=null;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
	boolean compile(String[] paths, String[] args) {
    	if (!isJavac()) {
            //if (IJ.debugMode) 
    		IJ.log("Compiler: javac not found");
            return false;
        }
        IJ.showStatus("compiling "+paths);
        String classpath = getClassPath(paths);
        Vector options = new Vector();
        if (generateDebuggingInfo)
            options.addElement("-g");
        validateTarget();
        options.addElement("-source");
        options.addElement(targets[target]);
        options.addElement("-target");
        options.addElement(targets[target]);
        options.addElement("-Xlint:unchecked");
        options.addElement("-deprecation");
        options.addElement("-classpath");
        options.addElement(classpath);
        if (args!=null) for(String arg : args) options.addElement(arg);
        	
        
        
        Vector sources = new Vector();
        if (sources!=null) for(String path : paths) sources.add(path);
        
        
        if (IJ.debugMode) {
            StringBuilder builder = new StringBuilder();
            builder.append("javac");
            for (int i=0; i< options.size(); i++){
                builder.append(" ");
                builder.append(options.get(i));
            }
            for (int i=0; i< sources.size(); i++){
                builder.append(" ");
                builder.append(sources.get(i));
            }
            IJ.log(builder.toString());
        }
        
        boolean errors = true;
        String s = "not compiled";
        if (compilerTool != null) {
            final StringWriter outputWriter = new StringWriter();
            errors = !compilerTool.compile(sources, options, outputWriter);
            s = outputWriter.toString();
        } else {
            errors = true;
        }
        
        if (errors)
            showErrors(s);
        else
            IJ.showStatus("done");
        return !errors;
     }
     
     // Returns a string containing the Java classpath, 
     // the path to the directory containing the plugin, 
     // and paths to any .jar files in the plugins folder.
     String getClassPath(String[] paths) {
        StringBuffer sb = new StringBuffer();
        sb.append(System.getProperty("java.class.path"));
        HashSet<String> pathSet = new HashSet<String>(); 
        for(String path : paths) {
        	 File f = new File(path);
             if (f!=null)  // add directory containing file to classpath
            	 pathSet.add(File.pathSeparator + f.getParent());
        }
        for(String path : pathSet) sb.append(path);
        String pluginsDir = Menus.getPlugInsPath();
        if (pluginsDir!=null)
            addJars(pluginsDir, sb);
        for(String path : pathSet) sb.append(path);
        String jarsPath = IJ.getDirectory("imagej")+"jars"+File.separator;
        addJars(jarsPath, sb);
        return sb.toString();
     }
     
    // Adds .jar files in plugins folder, and subfolders, to the classpath
    static void addJars(String path, StringBuffer sb) {
        String[] list = null;
        File f = new File(path);
        if (f.exists() && f.isDirectory())
            list = f.list();
        if (list==null)
            return;
        boolean isJarsFolder = path.endsWith("jars")|| path.endsWith("lib");
        if (!path.endsWith(File.separator))
			path += File.separator;
        for (int i=0; i<list.length; i++) {
            File f2 = new File(path+list[i]);
            if (f2.isDirectory())
                addJars(path+list[i], sb);
            else if (list[i].endsWith(".jar")&&(!list[i].contains("_")||isJarsFolder)) {
                sb.append(File.pathSeparator+path+list[i]);
            }
        }
    }
    
    void showErrors(String s) {
        if (errors==null || !errors.isVisible()) {
            errors = (Editor)IJ.runPlugIn("ij.plugin.frame.Editor", "");
            errors.setFont(new Font("Monospaced", Font.PLAIN, errors.getFontSize()));
        }
        if (errors!=null) {
            ImageJ ij = IJ.getInstance();
            if (ij!=null)
                s = ij.getInfo()+"\n \n"+s;
            errors.display("Errors", s);
        }
        IJ.showStatus("done (errors)");
    }

    // run the plugin using a new class loader
    void runPlugin(String name, String pluginDir, String arg, boolean newthread) {
    	if (name.endsWith(".java")) name = name.substring(0,name.length()-5);
    	if (name.endsWith(".class")) name = name.substring(0,name.length()-6);
        new PlugInExecuterMod(name, pluginDir, arg, newthread);
    }
    
    
    void validateTarget() {
        if (target>TARGET19)
            target = TARGET19;
        if (target<TARGET16)
            target = TARGET16;
        if (target>TARGET16 && IJ.javaVersion()<7)
            target = TARGET16;
        if (target>TARGET17 && IJ.javaVersion()<8)
            target = TARGET17;
        if (target>TARGET18 && IJ.javaVersion()<9)
            target = TARGET18;
        Prefs.set(TARGET_KEY, target);
    }
    
}

class PlugInExecuterMod implements Runnable {
    private String plugin;
    private String pluginDir;
    private Thread thread;
    private String arg;
    private boolean newthread;

    /** Create a new object that runs the specified plugin
        in a separate thread. */
    PlugInExecuterMod(String plugin, String pluginDir, String arg, boolean newthread) {
        this.plugin = plugin;
        this.pluginDir = pluginDir;
        this.arg = arg;
        this.newthread = newthread;
        if (newthread){
	        thread = new Thread(this, plugin);
	        thread.setPriority(Math.max(thread.getPriority()-2, Thread.MIN_PRIORITY));
	        thread.start();
	    } else run();
    }

    
    
    public void run() {
        IJ.resetEscape();
        IJ.runPlugIn("ij.plugin.ClassChecker", "");
        runCompiledPlugin(plugin, pluginDir, arg);
    }
    
    void runCompiledPlugin(String className, String classDir, String arg) {
        if (IJ.debugMode) IJ.log("Compiler: running \""+className+"\"");
        IJ.resetClassLoader();
        ClassLoader ijLoader = IJ.getClassLoader();
        URLClassLoader loader = null;
        if(classDir!=null && !classDir.isEmpty()) {
        	URL classUrl, jarsUrl;
			try {
				classUrl = new File(classDir).toURI().toURL();
				List<String> jars =jarsList(IJ.getDirectory("imagej") + "jars"+File.separator);
				URL[] urls = new URL[jars.size()+1];
				urls[0] = classUrl;
				for (int i = 1;i<urls.length;i++) {
					urls[i] = new File(jars.get(i-1)).toURI().toURL();
				}
				loader = new URLClassLoader(urls, ijLoader);
			} catch (MalformedURLException e) {
				if(IJ.debugMode) { 
					IJ.log(e.getLocalizedMessage());
					IJ.log(e.toString());
				}
			}          
        }
        
        
        Object thePlugIn = null;
        try { 
            thePlugIn = ((loader==null?ijLoader:loader).loadClass(className)).newInstance(); 
            if (thePlugIn instanceof PlugIn)
                ((PlugIn)thePlugIn).run(arg);
            else if (thePlugIn instanceof PlugInFilter)
                new PlugInFilterRunner(thePlugIn, className, "");
        }
        catch (ClassNotFoundException e) {
            if (className.indexOf('_')!=-1)
                IJ.error("Plugin or class not found: \"" + className + "\"\n(" + e+")");
        }
        catch (NoClassDefFoundError e) {
            String err = e.getMessage();
            if (IJ.debugMode) IJ.log("NoClassDefFoundError: "+err);
            int index = err!=null?err.indexOf("wrong name: "):-1;
            if (index>-1 && !className.contains(".")) {
                String className2 = err.substring(index+12, err.length()-1);
                className2 = className2.replace("/", ".");
                if (className2.equals(className)) { // Java 9 error format different
                    int spaceIndex = err.indexOf(" ");
                    if (spaceIndex>-1) {
                        className2 = err.substring(0, spaceIndex);
                        className2 = className2.replace("/", ".");
                    }
                }
                if (className2.equals(className))
                    IJ.error("Plugin not found: "+className2);
                else
                    runCompiledPlugin(className2, classDir, arg);
                return;
            }
            if (className.indexOf('_')!=-1)
                IJ.error("Plugin or class not found: \"" + className + "\"\n(" + e+")");
        }
        catch (Exception e) {
            IJ.handleException(e); //Marcel Boeglin 2013.09.01
        }
    }
    
    List<String> jarsList(String path) {
		ArrayList<String> result = new ArrayList<String>();
        String[] list = null;
        File f = new File(path);
        if (f.exists() && f.isDirectory())
            list = f.list();
        if (list==null)
            return result;
        boolean isJarsFolder = path.endsWith("jars")|| path.endsWith("lib");
        if (!path.endsWith(File.separator))
			path += File.separator;
        for (int i=0; i<list.length; i++) {
            File f2 = new File(path+list[i]);
            if (f2.isDirectory()) result.addAll(jarsList(path+list[i]));
            else if (list[i].endsWith(".jar")&&(!list[i].contains("_")||isJarsFolder)) {
            	result.add(path+list[i]);
            }
        }
        return result;
        
    }
    
}

abstract class CompilerToolMod {

    public static class JavaxCompilerTool extends CompilerToolMod {

        public boolean compile(List sources, List options, StringWriter log) {
            if (IJ.debugMode) IJ.log("Compiler: using javax.tool.JavaCompiler");
            try {
                JavaCompiler javac = getJavac();
                DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<JavaFileObject>();
                StandardJavaFileManager fileManager = javac.getStandardFileManager(diagnostics, null, null);
                Iterable<? extends JavaFileObject> compilationUnits = fileManager.getJavaFileObjectsFromStrings(sources);
                JavaCompiler.CompilationTask task =javac.getTask(log, fileManager, null, options, null, compilationUnits);
                fileManager.close();
                return task.call();
            } catch (Exception e) {
                PrintWriter printer = new PrintWriter(log);
                e.printStackTrace(printer);
                printer.flush();
            }
            return false;
        }

        protected JavaCompiler getJavac() throws Exception {
            JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
            return javac;
        }
    }

    public static class LegacyCompilerTool extends CompilerToolMod {
        protected static Class javacC;

        public boolean compile(List sources, List options, StringWriter log) {
            if (IJ.debugMode) IJ.log("Compiler: using com.sun.tools.javac");
            try {
                final String[] args = new String[sources.size() + options.size()];
                int argsIndex = 0;
                for (int optionsIndex = 0; optionsIndex < options.size(); optionsIndex++)
                    args[argsIndex++] = (String) options.get(optionsIndex);
                for (int sourcesIndex = 0; sourcesIndex < sources.size(); sourcesIndex++)
                    args[argsIndex++] = (String) sources.get(sourcesIndex);
                PrintWriter printer = new PrintWriter(log);
                Object javac = getJavac();
                Class[] compileTypes = new Class[] { String[].class, PrintWriter.class };
                Method compile = javacC.getMethod("compile", compileTypes);
                Object result = compile.invoke(javac, new Object[] { args, printer });
                printer.flush();
                return Integer.valueOf(0).equals(result);
            } catch (Exception e) {
                e.printStackTrace(new PrintWriter(log));
            }
            return false;
        }

        protected Object getJavac() throws Exception {
            if (javacC==null)
                javacC = Class.forName("com.sun.tools.javac.Main");
            return javacC.newInstance();
        }
    }

    public static CompilerToolMod getDefault() {
        CompilerToolMod javax = new JavaxCompilerTool();
        if (javax.isSupported())
            return javax;
        CompilerToolMod legacy = new LegacyCompilerTool();
        if (legacy.isSupported())
            return legacy;
        return null;
    }

    public abstract boolean compile(List sources, List options, StringWriter log);

    protected abstract Object getJavac() throws Exception;

    public boolean isSupported() {
        try {
            return null != getJavac();
        } catch (Exception e) {
            return false;
        }
    }
}


