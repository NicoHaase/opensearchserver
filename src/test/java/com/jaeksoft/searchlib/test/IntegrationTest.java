/**   
 * License Agreement for OpenSearchServer
 *
 * Copyright (C) 2013 Emmanuel Keller / Jaeksoft
 * 
 * http://www.open-search-server.com
 * 
 * This file is part of OpenSearchServer.
 *
 * OpenSearchServer is free software: you can redistribute it and/or
 * modify it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 * OpenSearchServer is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with OpenSearchServer. 
 *  If not, see <http://www.gnu.org/licenses/>.
 **/

package com.jaeksoft.searchlib.test;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.URISyntaxException;

import org.junit.BeforeClass;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;

import com.jaeksoft.searchlib.Server;
import com.jaeksoft.searchlib.test.rest.RestDeleteIndexTest;
import com.jaeksoft.searchlib.test.rest.RestDeleteTest;
import com.jaeksoft.searchlib.test.rest.RestFacetTest;
import com.jaeksoft.searchlib.test.rest.RestIndexCreateExistsListTest;
import com.jaeksoft.searchlib.test.rest.RestSchemaTest;
import com.jaeksoft.searchlib.test.rest.RestSearchTest;
import com.jaeksoft.searchlib.test.rest.RestUpdateTest;

@RunWith(Suite.class)
@SuiteClasses({ RestIndexCreateExistsListTest.class, RestSchemaTest.class, RestUpdateTest.class, RestSearchTest.class,
		RestDeleteTest.class, RestFacetTest.class, RestDeleteIndexTest.class })
/* RestAutocompletionTest */
public class IntegrationTest {

	public final static String INDEX_NAME = "oss_testing_suite";
	public final static String SERVER_URL = System.getProperty("OSS_TESTING_INSTANCE") == null ? "http://localhost:9090"
			: System.getProperty("OSS_TESTING_INSTANCE");

	@BeforeClass
	public static void beforeClass() throws IllegalAccessException, InstantiationException, InvocationTargetException,
			IOException, URISyntaxException {
		Server.start(null, false);
	}

}