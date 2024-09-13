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

import org.junit.runner.Runner;
import org.junit.runner.manipulation.NoTestsRemainException;
import org.junit.runners.Parameterized;
import org.junit.runners.parameterized.BlockJUnit4ClassRunnerWithParameters;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class FilteredParameterizedRunner extends Parameterized {

	private final List<Test> methods;

	public FilteredParameterizedRunner(
			Class<?> klass, List<Test> methods) throws Throwable {
		super(klass);
		this.methods = methods;
	}

	@Override
	protected List<Runner> getChildren() {
		List<Runner> allRunners = super.getChildren();
		List<Runner> filteredRunners = new ArrayList<>();

		for (Runner runner : allRunners) {
			BlockJUnit4ClassRunnerWithParameters parameterizedRunner =
					(BlockJUnit4ClassRunnerWithParameters)runner;

			if (parameterizedRunner == null) {
				continue;
			}

			String parameterName = parameterizedRunner.getDescription().getDisplayName()
					.replace("[", "")
					.replace("]", "");
			List<Test> filteredMethods = methods.stream()
					.filter(t -> parameterName.contentEquals(t.getParameterVariant()))
					.collect(Collectors.toList());

			if (filteredMethods.isEmpty()) {
				continue;
			}

			try {
				parameterizedRunner.filter(new MethodNameFilter(filteredMethods));
				filteredRunners.add(parameterizedRunner);
			} catch (NoTestsRemainException e) {
			}
		}

		return filteredRunners;
	}
}
