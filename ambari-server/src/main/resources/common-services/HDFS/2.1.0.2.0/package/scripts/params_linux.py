"""
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

"""

import status_params
import utils
import json
import os
import re

from ambari_commons.os_check import OSCheck

from resource_management.libraries.functions import conf_select
from resource_management.libraries.functions import format
from resource_management.libraries.functions.version import format_hdp_stack_version
from resource_management.libraries.functions.default import default
from resource_management.libraries.functions import get_klist_path
from resource_management.libraries.functions import get_kinit_path
from resource_management.libraries.script.script import Script
from resource_management.libraries.resources.hdfs_directory import HdfsDirectory
from resource_management.libraries.functions.format_jvm_option import format_jvm_option
from resource_management.libraries.functions.get_lzo_packages import get_lzo_packages

config = Script.get_config()
tmp_dir = Script.get_tmp_dir()

stack_name = default("/hostLevelParams/stack_name", None)
upgrade_direction = default("/commandParams/upgrade_direction", None)
stack_version_unformatted = str(config['hostLevelParams']['stack_version'])
hdp_stack_version = format_hdp_stack_version(stack_version_unformatted)

# New Cluster Stack Version that is defined during the RESTART of a Rolling Upgrade
version = default("/commandParams/version", None)

security_enabled = config['configurations']['cluster-env']['security_enabled']
hdfs_user = status_params.hdfs_user
root_user = "root"
hadoop_pid_dir_prefix = status_params.hadoop_pid_dir_prefix

# Some datanode settings
dfs_dn_addr = default('/configurations/hdfs-site/dfs.datanode.address', None)
dfs_dn_http_addr = default('/configurations/hdfs-site/dfs.datanode.http.address', None)
dfs_dn_https_addr = default('/configurations/hdfs-site/dfs.datanode.https.address', None)
dfs_http_policy = default('/configurations/hdfs-site/dfs.http.policy', None)
dfs_dn_ipc_address = config['configurations']['hdfs-site']['dfs.datanode.ipc.address']
secure_dn_ports_are_in_use = False

# hadoop default parameters
mapreduce_libs_path = "/usr/lib/hadoop-mapreduce/*"
hadoop_libexec_dir = "/usr/lib/hadoop/libexec"
hadoop_bin = "/usr/lib/hadoop/sbin"
hadoop_bin_dir = "/usr/bin"
hadoop_home = "/usr/lib/hadoop"
hadoop_secure_dn_user = hdfs_user
hadoop_conf_dir = conf_select.get_hadoop_conf_dir()

# hadoop parameters for 2.2+
if Script.is_hdp_stack_greater_or_equal("2.2"):
  mapreduce_libs_path = "/usr/hdp/current/hadoop-mapreduce-client/*"
  hadoop_libexec_dir = "/usr/hdp/current/hadoop-client/libexec"
  hadoop_bin = "/usr/hdp/current/hadoop-client/sbin"
  hadoop_bin_dir = "/usr/hdp/current/hadoop-client/bin"
  hadoop_home = "/usr/hdp/current/hadoop-client"

  if not security_enabled:
    hadoop_secure_dn_user = '""'
  else:
    dfs_dn_port = utils.get_port(dfs_dn_addr)
    dfs_dn_http_port = utils.get_port(dfs_dn_http_addr)
    dfs_dn_https_port = utils.get_port(dfs_dn_https_addr)
    # We try to avoid inability to start datanode as a plain user due to usage of root-owned ports
    if dfs_http_policy == "HTTPS_ONLY":
      secure_dn_ports_are_in_use = utils.is_secure_port(dfs_dn_port) or utils.is_secure_port(dfs_dn_https_port)
    elif dfs_http_policy == "HTTP_AND_HTTPS":
      secure_dn_ports_are_in_use = utils.is_secure_port(dfs_dn_port) or utils.is_secure_port(dfs_dn_http_port) or utils.is_secure_port(dfs_dn_https_port)
    else:   # params.dfs_http_policy == "HTTP_ONLY" or not defined:
      secure_dn_ports_are_in_use = utils.is_secure_port(dfs_dn_port) or utils.is_secure_port(dfs_dn_http_port)
    if secure_dn_ports_are_in_use:
      hadoop_secure_dn_user = hdfs_user
    else:
      hadoop_secure_dn_user = '""'



limits_conf_dir = "/etc/security/limits.d"

if Script.is_hdp_stack_greater_or_equal("2.0") and Script.is_hdp_stack_less_than("2.1") and not OSCheck.is_suse_family():
  # deprecated rhel jsvc_path
  jsvc_path = "/usr/libexec/bigtop-utils"
else:
  jsvc_path = "/usr/lib/bigtop-utils"

execute_path = os.environ['PATH'] + os.pathsep + hadoop_bin_dir
ulimit_cmd = "ulimit -c unlimited ; "

#security params
smoke_user_keytab = config['configurations']['cluster-env']['smokeuser_keytab']
hdfs_user_keytab = config['configurations']['hadoop-env']['hdfs_user_keytab']
falcon_user = config['configurations']['falcon-env']['falcon_user']

#exclude file
hdfs_exclude_file = default("/clusterHostInfo/decom_dn_hosts", [])
exclude_file_path = config['configurations']['hdfs-site']['dfs.hosts.exclude']
update_exclude_file_only = default("/commandParams/update_exclude_file_only",False)

klist_path_local = get_klist_path(default('/configurations/kerberos-env/executable_search_paths', None))
kinit_path_local = get_kinit_path(default('/configurations/kerberos-env/executable_search_paths', None))
#hosts
hostname = config["hostname"]
rm_host = default("/clusterHostInfo/rm_host", [])
slave_hosts = default("/clusterHostInfo/slave_hosts", [])
oozie_servers = default("/clusterHostInfo/oozie_server", [])
hcat_server_hosts = default("/clusterHostInfo/webhcat_server_host", [])
hive_server_host =  default("/clusterHostInfo/hive_server_host", [])
hbase_master_hosts = default("/clusterHostInfo/hbase_master_hosts", [])
hs_host = default("/clusterHostInfo/hs_host", [])
jtnode_host = default("/clusterHostInfo/jtnode_host", [])
namenode_host = default("/clusterHostInfo/namenode_host", [])
nm_host = default("/clusterHostInfo/nm_host", [])
ganglia_server_hosts = default("/clusterHostInfo/ganglia_server_host", [])
journalnode_hosts = default("/clusterHostInfo/journalnode_hosts", [])
zkfc_hosts = default("/clusterHostInfo/zkfc_hosts", [])
falcon_host = default("/clusterHostInfo/falcon_server_hosts", [])

has_ganglia_server = not len(ganglia_server_hosts) == 0
has_namenodes = not len(namenode_host) == 0
has_jobtracker = not len(jtnode_host) == 0
has_resourcemanager = not len(rm_host) == 0
has_histroryserver = not len(hs_host) == 0
has_hbase_masters = not len(hbase_master_hosts) == 0
has_slaves = not len(slave_hosts) == 0
has_oozie_server = not len(oozie_servers)  == 0
has_hcat_server_host = not len(hcat_server_hosts)  == 0
has_hive_server_host = not len(hive_server_host)  == 0
has_journalnode_hosts = not len(journalnode_hosts)  == 0
has_zkfc_hosts = not len(zkfc_hosts)  == 0
has_falcon_host = not len(falcon_host)  == 0


is_namenode_master = hostname in namenode_host
is_jtnode_master = hostname in jtnode_host
is_rmnode_master = hostname in rm_host
is_hsnode_master = hostname in hs_host
is_hbase_master = hostname in hbase_master_hosts
is_slave = hostname in slave_hosts

if has_ganglia_server:
  ganglia_server_host = ganglia_server_hosts[0]

#users and groups
yarn_user = config['configurations']['yarn-env']['yarn_user']
hbase_user = config['configurations']['hbase-env']['hbase_user']
oozie_user = config['configurations']['oozie-env']['oozie_user']
webhcat_user = config['configurations']['hive-env']['hcat_user']
hcat_user = config['configurations']['hive-env']['hcat_user']
hive_user = config['configurations']['hive-env']['hive_user']
smoke_user =  config['configurations']['cluster-env']['smokeuser']
smokeuser_principal =  config['configurations']['cluster-env']['smokeuser_principal_name']
mapred_user = config['configurations']['mapred-env']['mapred_user']
hdfs_principal_name = default('/configurations/hadoop-env/hdfs_principal_name', None)

user_group = config['configurations']['cluster-env']['user_group']
root_group = "root"
proxyuser_group =  config['configurations']['hadoop-env']['proxyuser_group']

#hadoop params
hdfs_log_dir_prefix = config['configurations']['hadoop-env']['hdfs_log_dir_prefix']
hadoop_root_logger = config['configurations']['hadoop-env']['hadoop_root_logger']

dfs_domain_socket_path = config['configurations']['hdfs-site']['dfs.domain.socket.path']
dfs_domain_socket_dir = os.path.dirname(dfs_domain_socket_path)

jn_edits_dir = config['configurations']['hdfs-site']['dfs.journalnode.edits.dir']

dfs_name_dir = config['configurations']['hdfs-site']['dfs.namenode.name.dir']

namenode_dirs_created_stub_dir = format("{hdfs_log_dir_prefix}/{hdfs_user}")
namenode_dirs_stub_filename = "namenode_dirs_created"

smoke_hdfs_user_dir = format("/user/{smoke_user}")
smoke_hdfs_user_mode = 0770


hdfs_namenode_formatted_mark_suffix = "/namenode-formatted/"
namenode_formatted_old_mark_dirs = ["/var/run/hadoop/hdfs/namenode-formatted", 
  format("{hadoop_pid_dir_prefix}/hdfs/namenode/formatted"),
  "/var/lib/hdfs/namenode/formatted"]
dfs_name_dirs = dfs_name_dir.split(",")
namenode_formatted_mark_dirs = []
for dn_dir in dfs_name_dirs:
 tmp_mark_dir = format("{dn_dir}{hdfs_namenode_formatted_mark_suffix}")
 namenode_formatted_mark_dirs.append(tmp_mark_dir)

# Use the namenode RPC address if configured, otherwise, fallback to the default file system
namenode_address = None
if 'dfs.namenode.rpc-address' in config['configurations']['hdfs-site']:
  namenode_rpcaddress = config['configurations']['hdfs-site']['dfs.namenode.rpc-address']
  namenode_address = format("hdfs://{namenode_rpcaddress}")
else:
  namenode_address = config['configurations']['core-site']['fs.defaultFS']

fs_checkpoint_dirs = config['configurations']['hdfs-site']['dfs.namenode.checkpoint.dir'].split(',')

dfs_data_dir = config['configurations']['hdfs-site']['dfs.datanode.data.dir']
dfs_data_dir = ",".join([re.sub(r'^\[.+\]', '', dfs_dir.strip()) for dfs_dir in dfs_data_dir.split(",")])

data_dir_mount_file = config['configurations']['hadoop-env']['dfs.datanode.data.dir.mount.file']

# HDFS High Availability properties
dfs_ha_enabled = False
dfs_ha_nameservices = default("/configurations/hdfs-site/dfs.nameservices", None)
dfs_ha_namenode_ids = default(format("/configurations/hdfs-site/dfs.ha.namenodes.{dfs_ha_nameservices}"), None)
dfs_ha_automatic_failover_enabled = default("/configurations/hdfs-site/dfs.ha.automatic-failover.enabled", False)

# hostname of the active HDFS HA Namenode (only used when HA is enabled)
dfs_ha_namenode_active = default("/configurations/hadoop-env/dfs_ha_initial_namenode_active", None)
# hostname of the standby HDFS HA Namenode (only used when HA is enabled)
dfs_ha_namenode_standby = default("/configurations/hadoop-env/dfs_ha_initial_namenode_standby", None)

namenode_id = None
namenode_rpc = None

if dfs_ha_namenode_ids:
  dfs_ha_namemodes_ids_list = dfs_ha_namenode_ids.split(",")
  dfs_ha_namenode_ids_array_len = len(dfs_ha_namemodes_ids_list)
  if dfs_ha_namenode_ids_array_len > 1:
    dfs_ha_enabled = True
if dfs_ha_enabled:
  for nn_id in dfs_ha_namemodes_ids_list:
    nn_host = config['configurations']['hdfs-site'][format('dfs.namenode.rpc-address.{dfs_ha_nameservices}.{nn_id}')]
    if hostname in nn_host:
      namenode_id = nn_id
      namenode_rpc = nn_host
  # With HA enabled namenode_address is recomputed
  namenode_address = format('hdfs://{dfs_ha_nameservices}')

if dfs_http_policy is not None and dfs_http_policy.upper() == "HTTPS_ONLY":
  https_only = True
  journalnode_address = default('/configurations/hdfs-site/dfs.journalnode.https-address', None)
else:
  https_only = False
  journalnode_address = default('/configurations/hdfs-site/dfs.journalnode.http-address', None)

if journalnode_address:
  journalnode_port = journalnode_address.split(":")[1]
  
  
if security_enabled:
  dn_principal_name = config['configurations']['hdfs-site']['dfs.datanode.kerberos.principal']
  dn_keytab = config['configurations']['hdfs-site']['dfs.datanode.keytab.file']
  dn_principal_name = dn_principal_name.replace('_HOST',hostname.lower())
  
  dn_kinit_cmd = format("{kinit_path_local} -kt {dn_keytab} {dn_principal_name};")
  
  nn_principal_name = config['configurations']['hdfs-site']['dfs.namenode.kerberos.principal']
  nn_keytab = config['configurations']['hdfs-site']['dfs.namenode.keytab.file']
  nn_principal_name = nn_principal_name.replace('_HOST',hostname.lower())
  
  nn_kinit_cmd = format("{kinit_path_local} -kt {nn_keytab} {nn_principal_name};")

  jn_principal_name = default("/configurations/hdfs-site/dfs.journalnode.kerberos.principal", None)
  if jn_principal_name:
    jn_principal_name = jn_principal_name.replace('_HOST', hostname.lower())
  jn_keytab = default("/configurations/hdfs-site/dfs.journalnode.keytab.file", None)
  jn_kinit_cmd = format("{kinit_path_local} -kt {jn_keytab} {jn_principal_name};")
else:
  dn_kinit_cmd = ""
  nn_kinit_cmd = ""
  jn_kinit_cmd = ""

import functools
#create partial functions with common arguments for every HdfsDirectory call
#to create hdfs directory we need to call params.HdfsDirectory in code
HdfsDirectory = functools.partial(
  HdfsDirectory,
  conf_dir=hadoop_conf_dir,
  hdfs_user=hdfs_user,
  security_enabled = security_enabled,
  keytab = hdfs_user_keytab,
  kinit_path_local = kinit_path_local,
  bin_dir = hadoop_bin_dir
)

# The logic for LZO also exists in OOZIE's params.py
io_compression_codecs = default("/configurations/core-site/io.compression.codecs", None)
lzo_enabled = io_compression_codecs is not None and "com.hadoop.compression.lzo" in io_compression_codecs.lower()
lzo_packages = get_lzo_packages(stack_version_unformatted)

exclude_packages = []
if not lzo_enabled:
  exclude_packages += lzo_packages
  
name_node_params = default("/commandParams/namenode", None)

#hadoop params
hadoop_env_sh_template = config['configurations']['hadoop-env']['content']

#hadoop-env.sh
java_home = config['hostLevelParams']['java_home']
java_version = int(config['hostLevelParams']['java_version'])

hadoop_heapsize = config['configurations']['hadoop-env']['hadoop_heapsize']
namenode_heapsize = config['configurations']['hadoop-env']['namenode_heapsize']
namenode_opt_newsize = config['configurations']['hadoop-env']['namenode_opt_newsize']
namenode_opt_maxnewsize = config['configurations']['hadoop-env']['namenode_opt_maxnewsize']
namenode_opt_permsize = format_jvm_option("/configurations/hadoop-env/namenode_opt_permsize","128m")
namenode_opt_maxpermsize = format_jvm_option("/configurations/hadoop-env/namenode_opt_maxpermsize","256m")

jtnode_opt_newsize = "200m"
jtnode_opt_maxnewsize = "200m"
jtnode_heapsize =  "1024m"
ttnode_heapsize = "1024m"

dtnode_heapsize = config['configurations']['hadoop-env']['dtnode_heapsize']
mapred_pid_dir_prefix = default("/configurations/mapred-env/mapred_pid_dir_prefix","/var/run/hadoop-mapreduce")
mapred_log_dir_prefix = default("/configurations/mapred-env/mapred_log_dir_prefix","/var/log/hadoop-mapreduce")

# ranger host
ranger_admin_hosts = default("/clusterHostInfo/ranger_admin_hosts", [])
has_ranger_admin = not len(ranger_admin_hosts) == 0

ambari_server_hostname = config['clusterHostInfo']['ambari_server_host'][0]

#ranger hdfs properties
policymgr_mgr_url = config['configurations']['admin-properties']['policymgr_external_url']
sql_connector_jar = config['configurations']['admin-properties']['SQL_CONNECTOR_JAR']
xa_audit_db_flavor = config['configurations']['admin-properties']['DB_FLAVOR']
xa_audit_db_name = config['configurations']['admin-properties']['audit_db_name']
xa_audit_db_user = config['configurations']['admin-properties']['audit_db_user']
xa_audit_db_password = config['configurations']['admin-properties']['audit_db_password']
xa_db_host = config['configurations']['admin-properties']['db_host']
repo_name = str(config['clusterName']) + '_hadoop'

hadoop_security_authentication = config['configurations']['core-site']['hadoop.security.authentication']
hadoop_security_authorization = config['configurations']['core-site']['hadoop.security.authorization']
fs_default_name = config['configurations']['core-site']['fs.defaultFS']
hadoop_security_auth_to_local = config['configurations']['core-site']['hadoop.security.auth_to_local']
hadoop_rpc_protection = config['configurations']['ranger-hdfs-plugin-properties']['hadoop.rpc.protection']
common_name_for_certificate = config['configurations']['ranger-hdfs-plugin-properties']['common.name.for.certificate']

repo_config_username = config['configurations']['ranger-hdfs-plugin-properties']['REPOSITORY_CONFIG_USERNAME']
repo_config_password = config['configurations']['ranger-hdfs-plugin-properties']['REPOSITORY_CONFIG_PASSWORD']

if security_enabled:
  sn_principal_name = default("/configurations/hdfs-site/dfs.secondary.namenode.kerberos.principal", "nn/_HOST@EXAMPLE.COM")
  sn_principal_name = sn_principal_name.replace('_HOST',hostname.lower())

ranger_env = config['configurations']['ranger-env']
ranger_plugin_properties = config['configurations']['ranger-hdfs-plugin-properties']
policy_user = config['configurations']['ranger-hdfs-plugin-properties']['policy_user']

#For curl command in ranger plugin to get db connector
jdk_location = config['hostLevelParams']['jdk_location']
java_share_dir = '/usr/share/java'
if has_ranger_admin:
  enable_ranger_hdfs = (config['configurations']['ranger-hdfs-plugin-properties']['ranger-hdfs-plugin-enabled'].lower() == 'yes')
  
  if xa_audit_db_flavor.lower() == 'mysql':
    jdbc_symlink_name = "mysql-jdbc-driver.jar"
    jdbc_jar_name = "mysql-connector-java.jar"
  elif xa_audit_db_flavor.lower() == 'oracle':
    jdbc_jar_name = "ojdbc6.jar"
    jdbc_symlink_name = "oracle-jdbc-driver.jar"
  elif xa_audit_db_flavor.lower() == 'postgres':
    jdbc_jar_name = "postgresql.jar"
    jdbc_symlink_name = "postgres-jdbc-driver.jar"
  elif xa_audit_db_flavor.lower() == 'sqlserver':
    jdbc_jar_name = "sqljdbc4.jar"
    jdbc_symlink_name = "mssql-jdbc-driver.jar"

  downloaded_custom_connector = format("{tmp_dir}/{jdbc_jar_name}")
  
  driver_curl_source = format("{jdk_location}/{jdbc_symlink_name}")
  driver_curl_target = format("{java_share_dir}/{jdbc_jar_name}")

  hdfs_ranger_plugin_config = {
    'username': repo_config_username,
    'password': repo_config_password,
    'hadoop.security.authentication': hadoop_security_authentication,
    'hadoop.security.authorization': hadoop_security_authorization,
    'fs.default.name': fs_default_name,
    'hadoop.security.auth_to_local': hadoop_security_auth_to_local,
    'hadoop.rpc.protection': hadoop_rpc_protection,
    'commonNameForCertificate': common_name_for_certificate,
    'dfs.datanode.kerberos.principal': dn_principal_name if security_enabled else '',
    'dfs.namenode.kerberos.principal': nn_principal_name if security_enabled else '',
    'dfs.secondary.namenode.kerberos.principal': sn_principal_name if security_enabled else ''
  }
  
  hdfs_ranger_plugin_repo = {
    'isActive': 'true',
    'config': json.dumps(hdfs_ranger_plugin_config),
    'description': 'hdfs repo',
    'name': repo_name,
    'repositoryType': 'hdfs',
    'assetType': '1'
  }
