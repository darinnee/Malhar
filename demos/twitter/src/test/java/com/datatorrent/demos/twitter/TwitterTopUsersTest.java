/**
 * Copyright (C) 2015 DataTorrent, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.datatorrent.demos.twitter;

import java.io.File;
import java.io.FileInputStream;

import com.datatorrent.api.LocalMode;
import com.datatorrent.contrib.twitter.TwitterSampleInput;

import org.apache.hadoop.conf.Configuration;
import org.junit.Test;

/**
 * Test the DAG declaration in local mode.
 */
public class TwitterTopUsersTest
{
  /**
   * @throws Exception
   */
  @Test
  public void testApplication() throws Exception
  {
    TwitterTopUsersApplication app = new TwitterTopUsersApplication();
    Configuration conf = new Configuration(false);
    conf.addResource("dt-site-topUsers.xml");
    conf.addResource(new FileInputStream(new File(
        "/home/dtadmin/.dt/dt-site.xml")));
    LocalMode lma = LocalMode.newInstance();
    lma.prepareDAG(app, conf);
    LocalMode.Controller lc = lma.getController();
    lc.run(120000);
  }
}
