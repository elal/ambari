#!/usr/bin/env python

'''
Licensed to the Apache Software Foundation (ASF) under one
or more contributor license agreements.  See the NOTICE file
distributed with this work for additional information
regarding copyright ownership.  The ASF licenses this file
to you under the Apache License, Version 2.0 (the
"License"); you may not use this file except in compliance
with the License.  You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
'''

from mock.mock import MagicMock, call, patch
from stacks.utils.RMFTestCase import *
import json
import sys


class TestMetadataServer(RMFTestCase):
  COMMON_SERVICES_PACKAGE_DIR = "ATLAS/0.1.0.2.3.0.0/package"
  STACK_VERSION = "2.3"

  @patch("shutil.copy2", new = MagicMock())
  @patch("os.path.isfile")
  def test_configure_default(self, isfile_mock):
    isfile_mock.return_value = True

    self.executeScript(self.COMMON_SERVICES_PACKAGE_DIR + "/scripts/metadata_server.py",
                       classname = "MetadataServer",
                       command = "configure",
                       config_file="default.json",
                       hdp_stack_version = self.STACK_VERSION,
                       target = RMFTestCase.TARGET_COMMON_SERVICES
    )

    self.assertResourceCalled('Directory', '/var/run/atlas',
                              owner = 'atlas',
                              group = 'hadoop',
                              recursive = True,
                              cd_access = 'a',
                              mode=0755
    )
    self.assertResourceCalled('Directory', '/etc/atlas/conf',
                              owner = 'atlas',
                              group = 'hadoop',
                              recursive = True,
                              cd_access = 'a',
                              mode=0755
    )
    self.assertResourceCalled('Directory', '/var/log/atlas',
                              owner = 'atlas',
                              group = 'hadoop',
                              recursive = True,
                              cd_access = 'a',
                              mode=0755
    )
    self.assertResourceCalled('Directory', '/var/lib/atlas/data',
                              owner = 'atlas',
                              group = 'hadoop',
                              recursive = True,
                              cd_access = 'a',
                              mode=0644
    )
    self.assertResourceCalled('Directory', '/var/lib/atlas/server/webapp',
                              owner = 'atlas',
                              group = 'hadoop',
                              recursive = True,
                              cd_access = 'a',
                              mode=0644
    )
    self.assertResourceCalled('File', '/etc/atlas/conf/application.properties',
                              content = InlineTemplate(self.getConfig()['configurations']['application-properties']['content']),
                              owner = 'atlas',
                              group = 'hadoop',
                              mode = 0644,
                              )
    self.assertResourceCalled('File', '/etc/atlas/conf/metadata-env.sh',
                              content = InlineTemplate(self.getConfig()['configurations']['metadata-env']['content']),
                              owner = 'atlas',
                              group = 'hadoop',
                              mode = 0755,
                              )
    self.assertResourceCalled('File', '/etc/atlas/conf/log4j.xml',
                              content = StaticFile('log4j.xml'),
                              owner = 'atlas',
                              group = 'hadoop',
                              mode = 0644,
                              )
    self.assertNoMoreResources()

  def test_start_default(self):
    self.executeScript(self.COMMON_SERVICES_PACKAGE_DIR + "/scripts/metadata_server.py",
                       classname = "MetadataServer",
                       command = "start",
                       config_file="default.json",
                       hdp_stack_version = self.STACK_VERSION,
                       target = RMFTestCase.TARGET_COMMON_SERVICES
    )
    self.assertResourceCalled('Execute', 'source /etc/atlas/conf/metadata-env.sh ; /usr/hdp/current/atlas-server/bin/metadata_start.py --port 21000',
                              not_if = 'ls /var/run/atlas/metadata.pid >/dev/null 2>&1 && ps -p `cat /var/run/atlas/metadata.pid` >/dev/null 2>&1',
                              user = 'atlas',
    )

  def test_stop_default(self):
    self.executeScript(self.COMMON_SERVICES_PACKAGE_DIR + "/scripts/metadata_server.py",
                       classname = "MetadataServer",
                       command = "stop",
                       config_file="default.json",
                       hdp_stack_version = self.STACK_VERSION,
                       target = RMFTestCase.TARGET_COMMON_SERVICES
    )
    self.assertResourceCalled('Execute', 'source /etc/atlas/conf/metadata-env.sh; /usr/hdp/current/atlas-server/bin/metadata_stop.py',
                              user = 'atlas',
    )
    self.assertResourceCalled('Execute', 'rm -f /var/run/atlas/metadata.pid',
    )
