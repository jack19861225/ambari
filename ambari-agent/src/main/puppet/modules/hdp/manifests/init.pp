#
#
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.
#
#
class hdp(
  $service_state = undef,
  $pre_installed_pkgs = undef
)
{

  import 'params.pp'
  include hdp::params

  Exec { logoutput => 'on_failure' }

  group { $hdp::params::user_group :
    ensure => present
  }


 ## Port settings
  if has_key($configuration, 'hdfs-site') {
    $hdfs-site = $configuration['hdfs-site']
    $namenode_port = hdp_get_port_from_url($hdfs-site["dfs.http.address"])
    $snamenode_port = hdp_get_port_from_url($hdfs-site["dfs.secondary.http.address"])
    $datanode_port = hdp_get_port_from_url($hdfs-site["dfs.datanode.http.address"])
  } else {
    $namenode_port = "50070"
    $snamenode_port = "50090"
    $datanode_port = "50075"
  }

  if has_key($configuration, 'mapred-site') {
    $mapred-site = $configuration['mapred-site']
    $jtnode_port = hdp_get_port_from_url($mapred-site["mapred.job.tracker.http.address"])
    $tasktracker_port = hdp_get_port_from_url($mapred-site["mapred.task.tracker.http.address"])
    $jobhistory_port = hdp_get_port_from_url($mapred-site["mapreduce.history.server.http.address"])
  } else {
    $jtnode_port = "50030"
    $tasktracker_port = "50060"
    $jobhistory_port = "51111"
  }

  if has_key($configuration, 'hbase-site') {
    $hbase-site = $configuration['hbase-site']
    $hbase_master_port = $hbase-site["hbase.master.info.port"]
    $hbase_rs_port = $hbase-site["hbase.regionserver.info.port"]
  } else {
    $hbase_master_port = "60010"
    $hbase_rs_port = "60030"
  }


  #TODO: think not needed and also there seems to be a puppet bug around this and ldap
  class { 'hdp::snmp': service_state => 'running'}

  class { 'hdp::create_smoke_user': }

  if ($pre_installed_pkgs != undef) {
    class { 'hdp::pre_install_pkgs': }
  }

  #turns off selinux
  class { 'hdp::set_selinux': }

  if ($service_state != 'uninstalled') {
    if ($hdp::params::lzo_enabled == true) {
      @hdp::lzo::package{ 32:}
      @hdp::lzo::package{ 64:}
    }
  }

  #TODO: treat consistently 
  if ($service_state != 'uninstalled') {
    if ($hdp::params::snappy_enabled == true) {
      include hdp::snappy::package
    }
  }

  Hdp::Package<|title == 'hadoop 32'|> ->   Hdp::Package<|title == 'hbase'|>
  Hdp::Package<|title == 'hadoop 64'|> ->   Hdp::Package<|title == 'hbase'|>

  #TODO: just for testing
  class{ 'hdp::iptables': 
    ensure => stopped,
  }


  
  hdp::package{ 'glibc':
    ensure       => 'present',
    size         => $size,
    java_needed  => false,
    lzo_needed   => false
  }

}

class hdp::pre_install_pkgs
{

  if ($service_state == 'installed_and_configured') {
    hdp::exec{ 'yum install $pre_installed_pkgs':
       command => "yum install -y $pre_installed_pkgs"
    }
  } elsif ($service_state == 'uninstalled') {
    hdp::exec{ 'yum erase $pre_installed_pkgs':
       command => "yum erase -y $pre_installed_pkgs"
    }
  }
}

class hdp::create_smoke_user()
{

  $smoke_group = $hdp::params::smoke_user_group
  $smoke_user = $hdp::params::smokeuser
  $security_enabled = $hdp::params::security_enabled

  if ( $smoke_group != $proxyuser_group) {
    group { $smoke_group :
      ensure => present
    }
  }
  
  if ($hdp::params::user_group != $proxyuser_group) {
    group { $proxyuser_group :
      ensure => present
    }
  }
  
  hdp::user { $smoke_user: 
    gid    => $hdp::params::user_group,
    groups => ["$proxyuser_group"]
  }

  ## Set smoke user uid to > 1000 for enable security feature
  $secure_uid = $hdp::params::smoketest_user_secure_uid
  $cmd_set_uid = "usermod -u ${secure_uid} ${smoke_user}"
  $cmd_set_uid_check = "id -u ${smoke_user} | grep ${secure_uid}"
  hdp::exec{ $cmd_set_uid:
   command => $cmd_set_uid,
   unless => $cmd_set_uid_check,
   require => Hdp::User[$smoke_user]
  }

  Group<|title == $smoke_group or title == $proxyuser_group|> -> Hdp::User[$smoke_user] 
}


class hdp::set_selinux()
{
 $cmd = "/bin/echo 0 > /selinux/enforce"
 hdp::exec{ $cmd:
    command => $cmd,
    unless => "head -n 1 /selinux/enforce | grep ^0$",
    onlyif => "test -f /selinux/enforce"
 }
}

define hdp::user(
  $gid = $hdp::params::user_group,
  $just_validate = undef,
  $groups = undef
)
{
  $user_info = $hdp::params::user_info[$name]
  if ($just_validate != undef) {
    $just_val  = $just_validate
  } elsif (($user_info == undef) or ("|${user_info}|" == '||')){ #tests for different versions of Puppet
    $just_val = false
  } else {
    $just_val = $user_info[just_validate]
  }
  
  if ($just_val == true) {
    exec { "user ${name} exists":
      command => "su - ${name} -c 'ls /dev/null' >/dev/null 2>&1",
      path    => ['/bin']
    }
  } else {
    user { $name:
      ensure     => present,
      managehome => true,
      gid        => $gid, #TODO either remove this to support LDAP env or fix it
      shell      => '/bin/bash',
      groups     => $groups 
    }
  }
}

     
define hdp::directory(
  $owner = undef,
  $group = $hdp::params::user_group,
  $mode  = undef,
  $ensure = directory,
  $force = undef,
  $service_state = 'running',
  $override_owner = false
  )
{
 if (($service_state == 'uninstalled') and ($wipeoff_data == true)) {
  file { $name :
    ensure => absent,
    owner  => $owner,
    group  => $group,
    mode   => $mode,
    force  => $force
   }
  } elsif ($service_state != 'uninstalled') {
    if $override_owner == true {
      file { $name :
      ensure => present,
      owner  => $owner,
      group  => $group,
      mode   => $mode,
      force  => $force
     }
    } else {
      file { $name :
      ensure => present,
      mode   => $mode,
      force  => $force
     }
    }
  }
}
#TODO: check on -R flag and use of recurse
define hdp::directory_recursive_create(
  $owner = undef,
  $group = $hdp::params::user_group,
  $mode = undef,
  $context_tag = undef,
  $ensure = directory,
  $force = undef,
  $service_state = 'running',
  $override_owner = true
  )
{

  hdp::exec {"mkdir -p ${name}" :
    command => "mkdir -p ${name}",
    creates => $name
  }
  #to take care of setting ownership and mode
  hdp::directory { $name :
    owner => $owner,
    group => $group,
    mode  => $mode,
    ensure => $ensure,
    force => $force,
    service_state => $service_state,
    override_owner => $override_owner
  }
  Hdp::Exec["mkdir -p ${name}"] -> Hdp::Directory[$name]
}

define hdp::directory_recursive_create_ignore_failure(
  $owner = undef,
  $group = $hdp::params::user_group,
  $mode = undef,
  $context_tag = undef,
  $ensure = directory,
  $force = undef,
  $service_state = 'running'
  )
{
  hdp::exec {"mkdir -p ${name} ; exit 0" :
    command => "mkdir -p ${name} ; exit 0",
    creates => $name
  }
    hdp::exec {"chown ${owner}:${group} ${name}; exit 0" :
    command => "chown ${owner}:${group} ${name}; exit 0"
  }
    hdp::exec {"chmod ${mode} ${name} ; exit 0" :
    command => "chmod ${mode} ${name} ; exit 0"
  }
  Hdp::Exec["mkdir -p ${name} ; exit 0"] -> Hdp::Exec["chown ${owner}:${group} ${name}; exit 0"] -> Hdp::Exec["chmod ${mode} ${name} ; exit 0"]
}

### helper to do exec
define hdp::exec(
  $command,
  $refreshonly = undef,
  $unless = undef,
  $onlyif = undef,
  $path = $hdp::params::exec_path,
  $user = undef,
  $creates = undef,
  $tries = 1,
  $timeout = 300,
  $try_sleep = undef,
  $initial_wait = undef,
  $logoutput = 'on_failure',
  $cwd = undef
)
{
     


  if (($initial_wait != undef) and ($initial_wait != "undef")) {
    #passing in creates and unless so dont have to wait if condition has been acheived already
    hdp::wait { "service ${name}" : 
      wait_time => $initial_wait,
      creates   => $creates,
      unless    => $unless,
      onlyif    => $onlyif,
      path      => $path
    }
  }
  
  exec { $name :
    command     => $command,
    refreshonly => $refreshonly,
    path        => $path,
    user        => $user,
    creates     => $creates,
    unless      => $unless,
    onlyif      => $onlyif,
    tries       => $tries,
    timeout     => $timeout,
    try_sleep   => $try_sleep,
    logoutput   => $logoutput,
    cwd         => $cwd
  }
  
  anchor{ "hdp::exec::${name}::begin":} -> Exec[$name] -> anchor{ "hdp::exec::${name}::end":} 
  if (($initial_wait != undef) and ($initial_wait != "undef")) {
    Anchor["hdp::exec::${name}::begin"] -> Hdp::Wait["service ${name}"] -> Exec[$name]
  }
}

#### utilities for waits
define hdp::wait(
  $wait_time,
  $creates = undef,
  $unless = undef,
  $onlyif = undef,
  $path = undef #used for unless
)   
{
  exec { "wait ${name} ${wait_time}" :
    command => "/bin/sleep ${wait_time}",
    creates => $creates,
    unless  => $unless,
    onlyif  => $onlyif,
    path    => $path
  } 
}

##### temp

class hdp::iptables($ensure)
{
  #TODO: just temp so not considering things like saving firewall rules
  service { 'iptables':
    ensure => $ensure
  }
}
