package com.seeq.eclipse;

import java.io.*;
import java.util.*;
import java.util.regex.*;

import org.eclipse.core.resources.*;
import org.eclipse.core.runtime.*;
import org.eclipse.equinox.app.*;
import org.osgi.framework.*;
import org.w3c.dom.Document;

import com.teamcenter.bmide.install.dependency.TemplateDependency;
import com.teamcenter.bmide.install.dependency.TemplateDependencyReader;
import com.teamcenter.bmide.server.ui.util.UITemplateDependencyUtil;

public class ImportProjects implements org.eclipse.ui.IStartup {

	private static final String ARG_IMPORT = "-import";
	private static final String ARG_TEMPLATES = "-templates";
	private static final String ARG_CLOSE = "-close";

	private String[] getImportPaths() {
		BundleContext context = Activator.getContext();
		ServiceReference<?> ser = context.getServiceReference(IApplicationContext.class.getName());
		IApplicationContext iac = (IApplicationContext) context.getService(ser);
		String[] args = (String[]) iac.getArguments().get(IApplicationContext.APPLICATION_ARGS);
		List<String> importPath = new ArrayList<String>();
		for (int i = 0; i < args.length; i++) {
			String arg = args[i];
			if (arg.compareToIgnoreCase(ARG_IMPORT) == 0) {
				i++;
				if (i < args.length) {
					importPath.add(args[i]);
				}
			}
		}

		return importPath.toArray(new String[importPath.size()]);
	}

	private String getTemplatesPath() {
		BundleContext context = Activator.getContext();
		ServiceReference<?> ser = context.getServiceReference(IApplicationContext.class.getName());
		IApplicationContext iac = (IApplicationContext) context.getService(ser);
		String[] args = (String[]) iac.getArguments().get(IApplicationContext.APPLICATION_ARGS);
		String templatesPath = "";
		for (int i = 0; i < args.length; i++) {
			String arg = args[i];
			if (arg.compareToIgnoreCase(ARG_TEMPLATES) == 0) {
				i++;
				if (i < args.length) {
					templatesPath = args[i];
				}
			}
		}

		return templatesPath;
	}

	private boolean getClose() {
		BundleContext context = Activator.getContext();
		ServiceReference<?> ser = context.getServiceReference(IApplicationContext.class.getName());
		IApplicationContext iac = (IApplicationContext) context.getService(ser);
		String[] args = (String[]) iac.getArguments().get(IApplicationContext.APPLICATION_ARGS);
		for (int i = 0; i < args.length; i++) {
			String arg = args[i];
			if (arg.compareToIgnoreCase(ARG_CLOSE) == 0) {
				return true;
			}
		}

		return false;
	}

	private List<File> findFilesRecursively(String path, String pattern, List<File> returnedList) {
		File root = new File(path);
		File[] list = root.listFiles();

		if (list == null)
			return returnedList;

		for (File f : list) {
			if (f.isDirectory()) {
				this.findFilesRecursively(f.getAbsolutePath(), pattern, returnedList);
			} else {
				if (Pattern.matches(pattern, f.getName()) == true) {
					returnedList.add(f);
				}
			}
		}

		return returnedList;
	}

	@SuppressWarnings("unchecked")
	@Override
	public void earlyStartup() {

		String[] importPaths = this.getImportPaths();
		String templatesPath = getTemplatesPath();
		boolean close = getClose();

		for (String importPath : importPaths) {
			LogUtil.info(String.format("Searching for projects in %s", importPath));
			List<File> projectFiles = this.findFilesRecursively(importPath, "\\.project", new ArrayList<File>());

			for (File projectFile : projectFiles) {
				try {
					IWorkspace workspace = ResourcesPlugin.getWorkspace();
					IProjectDescription description = workspace
							.loadProjectDescription(new Path(projectFile.toString()));
					IProject project = workspace.getRoot().getProject(description.getName());
					if (!project.isOpen() && !project.exists()) {
						LogUtil.info(String.format("Importing project %s %s", description.getName(),
								description.getLocationURI()));
						project.create(description, null);
						project.open(null);

						try {
							TemplateDependencyReader templateDependencyReader = new TemplateDependencyReader();
							Document dependencyXMLDocument = templateDependencyReader.readXmlFile(project
									.getFolder("extensions").getFile("dependency.xml").getLocation().toOSString());
							TemplateDependency templateDependency = (TemplateDependency) templateDependencyReader
									.buildObject(dependencyXMLDocument);
							project.setPersistentProperty(new QualifiedName("", "SOLUTION_NAME"),
									templateDependency.getName());
							List<String> prefixes = templateDependency.getPrefixes();
							if (prefixes != null && prefixes.size() > 0) {
								project.setPersistentProperty(new QualifiedName("", "prefixes"), prefixes.get(0));
							}
						} catch (Exception e) {
							e.printStackTrace();
						}

						project.setPersistentProperty(new QualifiedName("", "TEAMCENTER_MODEL_FOLDER"), templatesPath);
						if (close) {
							project.close(null);
						}
					}
				} catch (CoreException e) {
					LogUtil.error(e);
				}
			}
		}
	}
}
