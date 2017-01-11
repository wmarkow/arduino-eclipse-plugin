package jUnit;

import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.TreeMap;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import io.sloeber.core.api.BoardDescriptor;
import io.sloeber.core.api.BoardsManager;
import io.sloeber.core.api.CodeDescriptor;
import io.sloeber.core.api.CompileOptions;
import io.sloeber.core.api.ConfigurationDescriptor;

@SuppressWarnings("nls")
@RunWith(Parameterized.class)
public class CreateAndCompileExamples {
	private static int mCounter = 0;
	private CodeDescriptor myCodeDescriptor;
	private BoardDescriptor myBoardid;

	public CreateAndCompileExamples(String name, BoardDescriptor boardid, CodeDescriptor codeDescriptor) {
		this.myBoardid = boardid;
		this.myCodeDescriptor = codeDescriptor;

	}

	@SuppressWarnings("rawtypes")
	@Parameters(name = "{index}: {0}")
	public static Collection examples() {
		Map<String, String> myOptions = new HashMap<>();
		String[] lines = new String("").split("\n"); //$NON-NLS-1$
		for (String curLine : lines) {
			String[] values = curLine.split("=", 2); //$NON-NLS-1$
			if (values.length == 2) {
				myOptions.put(values[0], values[1]);
			}
		}
		BoardDescriptor leonardoBoardid = BoardsManager.getBoardID("package_index.json", "arduino",
				"Arduino AVR Boards", "leonardo", myOptions);
		if (leonardoBoardid == null) {
			fail("leonardo Board not found");
			return null;
		}
		BoardDescriptor unoBoardid = BoardsManager.getBoardID("package_index.json", "arduino", "Arduino AVR Boards",
				"uno", myOptions);
		if (unoBoardid == null) {
			fail("uno Board not found");
			return null;
		}

		LinkedList<Object[]> examples = new LinkedList<>();
		TreeMap<String, IPath> exampleFolders = BoardsManager.getAllExamples(null);
		for (Map.Entry<String, IPath> curexample : exampleFolders.entrySet()) {
			ArrayList<Path> paths = new ArrayList<>();

			paths.add(new Path(curexample.getValue().toString()));
			CodeDescriptor codeDescriptor = CodeDescriptor.createExample(false, paths);
			if (isExampleOkForLeonardo(curexample.getKey())) {
				Object[] theData = new Object[] { "leonardo :" + curexample.getKey(), leonardoBoardid, codeDescriptor };

				examples.add(theData);
			}
			if (isExampleOkForUno(curexample.getKey())) {
				Object[] theData = new Object[] { "Uno :" + curexample.getKey(), unoBoardid, codeDescriptor };

				examples.add(theData);
			}
		}

		return examples;

	}

	private static boolean isExampleOkForUno(String key) {
		final String[] notOkForUno = { "Firmataexamples-StandardFirmataWiFi", "examples-04.Communication-MultiSerial",
				"examples-09.USB-Keyboard-KeyboardLogout", "examples-09.USB-Keyboard-KeyboardMessage",
				"examples-09.USB-Keyboard-KeyboardReprogram", "examples-09.USB-Keyboard-KeyboardSerial",
				"examples-09.USB-KeyboardAndMouseControl", "examples-09.USB-Mouse-ButtonMouseControl",
				"examples-09.USB-Mouse-JoystickMouseControl", };
		if (key.startsWith("Esploraexamples"))
			return false;
		if (key.startsWith("TFTexamples-Esplora-EsploraTFTTemp"))
			return false;

		if (key.contains("Firmata"))
			return false;
		if (Arrays.asList(notOkForUno).contains(key))
			return false;
		return true; // default everything is fine
	}

	private static boolean isExampleOkForLeonardo(String key) {
		final String[] notOkForLeonardo = { "Esploraexamples-Beginners-EsploraJoystickMouse",
				"Esploraexamples-Experts-EsploraKart", "Esploraexamples-Experts-EsploraTable",
				"Firmataexamples-StandardFirmataWiFi" };
		if (key.contains("Firmata"))
			return false;
		if (key.startsWith("TFTexamples-Esplora-EsploraTFTTemp"))
			return false;
		if (Arrays.asList(notOkForLeonardo).contains(key))
			return false;
		return true; // default everything is fine
	}

	/*
	 * In new new installations (of the Sloeber development environment) the
	 * installer job will trigger downloads These mmust have finished before we
	 * can start testing
	 */
	@BeforeClass
	public static void WaitForInstallerToFinish() {
		installAdditionalBoards();
		Shared.waitForAllJobsToFinish();
	}

	public static void installAdditionalBoards() {
		String[] packageUrlsToAdd = { "http://arduino.esp8266.com/stable/package_esp8266com_index.json" };
		BoardsManager.addPackageURLs(new HashSet<>(Arrays.asList(packageUrlsToAdd)), true);
		BoardsManager.installAllLatestPlatforms();

	}

	@Test
	public void testExamples() {
		BuildAndVerify(this.myBoardid, this.myCodeDescriptor);

	}

	public static void BuildAndVerify(BoardDescriptor boardid, CodeDescriptor codeDescriptor) {

		IProject theTestProject = null;

		NullProgressMonitor monitor = new NullProgressMonitor();
		String projectName = String.format("%03d_", new Integer(mCounter++)) + boardid.getBoardID()
				+ codeDescriptor.getExamples().get(0).lastSegment();
		try {

			theTestProject = boardid.createProject(projectName, null, ConfigurationDescriptor.getDefaultDescriptors(),
					codeDescriptor, new CompileOptions(null), monitor);
			Shared.waitForAllJobsToFinish(); // for the indexer
		} catch (Exception e) {
			e.printStackTrace();
			fail("Failed to create the project:" + projectName);
			return;
		}
		try {
			theTestProject.build(IncrementalProjectBuilder.FULL_BUILD, monitor);
			if (Shared.hasBuildErrors(theTestProject)) {
				// try again because the libraries may not yet been added
				Shared.waitForAllJobsToFinish(); // for the indexer
				theTestProject.build(IncrementalProjectBuilder.FULL_BUILD, monitor);
				if (Shared.hasBuildErrors(theTestProject)) {
					// give up
					fail("Failed to compile the project:" + projectName + " build errors");
				}
			}
		} catch (CoreException e) {
			e.printStackTrace();
			fail("Failed to compile the project:" + projectName + " exception");
		}
	}

}