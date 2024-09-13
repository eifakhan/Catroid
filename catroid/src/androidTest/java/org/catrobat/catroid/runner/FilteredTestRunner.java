/*
 * Catroid: An on-device visual programming system for Android devices
 * Copyright (C) 2010-2025 The Catrobat Team
 * (<http://developer.catrobat.org/credits>)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * An additional term exception under section 7 of the GNU Affero
 * General Public License, version 3, is available at
 * http://developer.catrobat.org/license_additional_term
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.catrobat.catroid.runner;

import android.util.Log;

import org.junit.runner.Description;
import org.junit.runner.RunWith;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.Parameterized;
import org.junit.runners.ParentRunner;
import org.junit.runners.model.InitializationError;

import java.util.ArrayList;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.List;

import androidx.test.platform.app.InstrumentationRegistry;
import dalvik.system.DexFile;

public class FilteredTestRunner extends ParentRunner<ParentRunner> {

	private static final String TAG = FilteredTestRunner.class.getSimpleName();

	private final Dictionary<String, List<Test>> tests;
	private final List<ParentRunner> children;
	private PackagePath pathAnnotation;
	private FailedTests failedTestsAnnotation;

	public FilteredTestRunner(Class<?> suite) throws InitializationError {
		super(suite);
		setAnnotations(suite);
		this.tests = getTestMethodsToRun();
		this.children = getChildRunners();
	}

	private void setAnnotations(Class<?> suite) throws InitializationError {
		pathAnnotation = suite.getAnnotation(PackagePath.class);
		if (pathAnnotation == null) {
			throw new InitializationError(
					String.format("class '%s' must have a PackagePath annotation",
							suite.getName()));
		}

		failedTestsAnnotation = suite.getAnnotation(FailedTests.class);
		if (failedTestsAnnotation == null) {
			throw new InitializationError(
					String.format("class '%s' must have a FailedTests annotation",
							suite.getName()));
		}
	}

	private Dictionary<String, List<Test>> getTestMethodsToRun() {
		Dictionary<String, List<Test>> groupedTests = new Hashtable<>();

		List<Test> tests = new JenkinsResultParser()
				.parseTests(List.of(failedTestsAnnotation.value().split("\n")));

		for (Test test : tests) {
			List<Test> testsForClass = groupedTests.get(test.getClassName());
			if (testsForClass == null) {
				testsForClass = new ArrayList<>();
				groupedTests.put(test.getClassName(), testsForClass);
			}
			testsForClass.add(test);
		}

		return groupedTests;
	}

	private List<ParentRunner> getChildRunners() throws InitializationError {
		List<ParentRunner> runners = new ArrayList<>();

		try {
			// maybe change this to also rerun unit tests? here we are getting the package
			// where the instrumented test is run.
			String packageCodePath =
					InstrumentationRegistry.getInstrumentation().getContext().getPackageCodePath();
			DexFile dexFile = new DexFile(packageCodePath);

			Enumeration<String> iter = dexFile.entries();
			while (iter.hasMoreElements()) {
				String fullyQualifiedClassName = iter.nextElement();
				String[] parts = fullyQualifiedClassName.split("\\.");
				String className = parts[parts.length - 1];

				if (!fullyQualifiedClassName.contains(pathAnnotation.value()) ||
					tests.get(className) == null ||
					(!className.endsWith("Test") && !className.contentEquals("CatrobatTestRunner"))) {
					continue;
				}

				List<Test> testsToRerun = tests.get(className);
				Class<?> testClass = Class.forName(fullyQualifiedClassName);
				RunWith runWithAnnotation = testClass.getAnnotation(RunWith.class);

				if (runWithAnnotation != null && runWithAnnotation.value() == Parameterized.class) {
					runners.add(new FilteredParameterizedRunner(testClass, testsToRerun));
				} else {
					BlockJUnit4ClassRunner classRunner = new BlockJUnit4ClassRunner(testClass);
					classRunner.filter(new MethodNameFilter(testsToRerun));
					runners.add(classRunner);
				}
			}
		} catch (Throwable t) {
			Log.e(TAG, t.getMessage(), t);
			throw new InitializationError("Exception during loading Test classes from Dex");
		}

		return runners;
	}

	@Override
	protected List<ParentRunner> getChildren() {
		return children;
	}

	@Override
	protected Description describeChild(ParentRunner child) {
		return Description.createSuiteDescription(child.getTestClass().getName());
	}

	@Override
	protected void runChild(ParentRunner child, RunNotifier notifier) {
		child.run(notifier);
	}

	class JenkinsResultParser {
		public List<Test> parseTests(List<String> jenkinsOutput) {
			List<Test> tests = new ArrayList<>();
			for (String line : jenkinsOutput) {
				while (line.contains("/") && !line.startsWith("org.catrobat.catroid")) {
					line = line.split("/", 2)[1].stripLeading();
				}

				while (!line.split("\\.")[0].endsWith("Test") &&
						!line.startsWith("CatrobatTestRunner")) {
					line = line.split("\\.", 2)[1];
				}

				String[] parts = line.split("\\.", 2);
				String className = parts[0];
				String methodName = parts[1];
				String parameterName = "";

				if (methodName.contains("[")) {
					String[] methodParts = methodName.split("\\[");
					methodName = methodParts[0];
					parameterName = methodParts[1].replace("]", "");

					if (className.equalsIgnoreCase("CatrobatTestRunner") &&
						!parameterName.endsWith(".catrobat")) {
						parameterName += ".catrobat";
					}
				}

				tests.add(new Test(className, methodName, parameterName));
			}

			return tests;
		}
	}
}
