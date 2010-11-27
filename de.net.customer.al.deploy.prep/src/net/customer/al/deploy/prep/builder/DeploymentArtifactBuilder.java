package net.customer.al.deploy.prep.builder;

import java.io.IOException;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.IResourceDeltaVisitor;
import org.eclipse.core.resources.IResourceVisitor;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

public class DeploymentArtifactBuilder extends IncrementalProjectBuilder {

	private static final String XML = "xml";

	private static final String PRO = "properties";

	public static final String BUILDER_ID = "net.customer.al.deploy.prep.deploymentArtifactBuilder";

	private static final String MARKER_TYPE = "net.customer.al.deploy.prep.xmlProblem";

	private SAXParserFactory parserFactory;
	
	private final Set<String> placeholders = new TreeSet<String>();

	private boolean canContainPlaceholder(IResource resource) {
		String s = resource.getParent().getName();
		String e = resource.getFileExtension();
		int t = resource.getType();

		boolean success = true;
		success = success && (t == IResource.FILE);
		if (!success)
			return false;

		success = success
				&& (null != e && e.trim().length() > 0 && (XML
						.equalsIgnoreCase(e) || PRO.equalsIgnoreCase(e)));
		if (!success)
			return false;

		success = success && ("properties".equalsIgnoreCase(s));
		return success;
	}

	class DeltaVisitor implements IResourceDeltaVisitor {
		/*
		 * (non-Javadoc)
		 * 
		 * @see
		 * org.eclipse.core.resources.IResourceDeltaVisitor#visit(org.eclipse
		 * .core.resources.IResourceDelta)
		 */
		public boolean visit(IResourceDelta delta) throws CoreException {
			IResource resource = delta.getResource();
			if (canContainPlaceholder(resource)) {
				prepareDeployProperties(resource);
			}
			/*
			 * switch delta.getKind() case IResourceDelta.REMOVED: break; case
			 * IResourceDelta.CHANGED: break; }
			 */
			// return true to continue visiting children.
			return true;
		}
	}

	class ResourceVisitor implements IResourceVisitor {
		public boolean visit(IResource resource) {
			if (canContainPlaceholder(resource)) {
				prepareDeployProperties(resource);
			}
			// return true to continue visiting children.
			return true;
		}
	}

	class XMLCfgHandler extends DefaultHandler {

		private final StringBuilder b = new StringBuilder();
		
		private IFile file;

		public XMLCfgHandler(IFile file) {
			this.file = file;
		}

		private void addMarker(SAXParseException e, int severity) {
			DeploymentArtifactBuilder.this.addMarker(file, e.getMessage(), e
					.getLineNumber(), severity);
		}

		public void error(SAXParseException exception) throws SAXException {
			addMarker(exception, IMarker.SEVERITY_ERROR);
		}

		public void fatalError(SAXParseException exception) throws SAXException {
			addMarker(exception, IMarker.SEVERITY_ERROR);
		}

		public void warning(SAXParseException exception) throws SAXException {
			addMarker(exception, IMarker.SEVERITY_WARNING);
		}

		@Override
		public void characters(char[] arg0, int arg1, int arg2)
				throws SAXException {
			b.append(arg0);
		}

		@Override
		public void endElement(String arg0, String arg1, String arg2)
				throws SAXException {
			if(b.length()>0) {
				String s = b.toString();
				scanPlaceholder(s, 0, placeholders);
				b.setLength(0);
			}
		}

		@Override
		public void startElement(String arg0, String arg1, String arg2,
				Attributes attr) throws SAXException {
			int l = attr.getLength();
			for (int i = 0; i < l; i++) {
				String v = attr.getValue(i);
				scanPlaceholder(v, 0, placeholders);
			}
		}

	}

	private static void scanPlaceholder(String string, int fromIndex,
			Set<String> placeholders) {
		int s = string.indexOf("${", fromIndex);
		if (s < 0)
			return;
		s += 2;
		int e = string.indexOf('}', s);
		if (e < 0)
			return;
		String ph = string.substring(s, e);
		if (!ph.isEmpty())
			placeholders.add(ph);
		scanPlaceholder(string, e, placeholders);
	}

	public static void main(String[] args) {
		String s = "wiejfwe wefew efw s  werwer ${XX.XX.XX} ... .. .${}. . ..${XX.XX.XX}. ... ... ${CCCCCC} b ${DD.DD.DD} .. .. .. .. .. ";
		Set<String> set = new TreeSet<String>();
		scanPlaceholder(s, 0, set);
		System.out.println(set);
	}

	private void addMarker(IFile file, String message, int lineNumber,
			int severity) {
		try {
			IMarker marker = file.createMarker(MARKER_TYPE);
			marker.setAttribute(IMarker.MESSAGE, message);
			marker.setAttribute(IMarker.SEVERITY, severity);
			if (lineNumber == -1) {
				lineNumber = 1;
			}
			marker.setAttribute(IMarker.LINE_NUMBER, lineNumber);
		} catch (CoreException e) {
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.core.internal.events.InternalBuilder#build(int,
	 * java.util.Map, org.eclipse.core.runtime.IProgressMonitor)
	 */
	protected IProject[] build(int kind, Map args, IProgressMonitor monitor)
			throws CoreException {
		if (kind == FULL_BUILD) {
			fullBuild(monitor);
		} else {
			IResourceDelta delta = getDelta(getProject());
			if (delta == null) {
				fullBuild(monitor);
			} else {
				incrementalBuild(delta, monitor);
			}
		}
		return null;
	}

	void prepareDeployProperties(IResource resource) {
		if (resource instanceof IFile) {
			final String rext = resource.getFileExtension();
			IFile file = (IFile) resource;
			deleteMarkers(file);
			if (".xml".equalsIgnoreCase(rext)) {
				XMLCfgHandler reporter = new XMLCfgHandler(file);
				try {
					getParser().parse(file.getContents(), reporter);
				} catch (Exception e1) {
				}
			} else if ("properties".equalsIgnoreCase(rext)) {
				Properties p = new Properties();
				try {
					p.load(file.getContents());
					for (Object o : p.keySet()) {
						String s = p.getProperty(String.valueOf(o));
						if(null != s) {
							scanPlaceholder(s, 0, placeholders);
						}
					}
				} catch (Exception e) {
				}
			}
			mergeResults();
			
			placeholders.clear();
			
		}
	}

	private void mergeResults() {
		
	}
	
	private void deleteMarkers(IFile file) {
		try {
			file.deleteMarkers(MARKER_TYPE, false, IResource.DEPTH_ZERO);
		} catch (CoreException ce) {
		}
	}

	protected void fullBuild(final IProgressMonitor monitor)
			throws CoreException {
		try {
			getProject().accept(new ResourceVisitor());
		} catch (CoreException e) {
		}
	}

	private SAXParser getParser() throws ParserConfigurationException,
			SAXException {
		if (parserFactory == null) {
			parserFactory = SAXParserFactory.newInstance();
		}
		return parserFactory.newSAXParser();
	}

	protected void incrementalBuild(IResourceDelta delta,
			IProgressMonitor monitor) throws CoreException {
		// the visitor does the work.
		delta.accept(new DeltaVisitor());
	}
}
