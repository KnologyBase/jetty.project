//
//  ========================================================================
//  Copyright (c) 1995-2019 Mort Bay Consulting Pty. Ltd.
//  ------------------------------------------------------------------------
//  All rights reserved. This program and the accompanying materials
//  are made available under the terms of the Eclipse Public License v1.0
//  and Apache License v2.0 which accompanies this distribution.
//
//      The Eclipse Public License is available at
//      http://www.eclipse.org/legal/epl-v10.html
//
//      The Apache License v2.0 is available at
//      http://www.opensource.org/licenses/apache2.0.php
//
//  You may elect to redistribute this code under either of these licenses.
//  ========================================================================
//

package org.eclipse.jetty.tests.distribution;

import java.io.File;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnJre;
import org.junit.jupiter.api.condition.EnabledOnJre;
import org.junit.jupiter.api.condition.JRE;

public class JpmsActivatedTests
{
    @Test
    @DisabledOnJre(JRE.JAVA_8)
    public void jpms_activated() throws Exception
    {
        DistributionRunner distributionRunner = DistributionRunner.Builder.newInstance() //
                .jettyVersion(System.getProperty("jetty_version")) //
                .mavenLocalRepository(System.getProperty("mavenRepoPath")) //
                .waitStartTime(30) //
                .build(); //
        try
        {
            distributionRunner.start("--create-startd", "--approve-all-licenses", "--add-to-start=resources,server,http,webapp,deploy,jsp,jmx,jmx-remote,servlet,servlets");
            distributionRunner.stop();

            File war = distributionRunner.resolveArtifact("org.eclipse.jetty.tests:test-simple-webapp:war:" + System.getProperty("jetty_version"));
            distributionRunner.installWarFile(war, "test");
            distributionRunner.start("--jpms");
            distributionRunner.assertLogsContains("Started @");
            distributionRunner.assertUrlStatus("/test/index.jsp", 200);
            distributionRunner.assertUrlContains("/test/index.jsp", "Hello");
        } finally {
            distributionRunner.stop();
            distributionRunner.cleanup();
        }

    }
}
