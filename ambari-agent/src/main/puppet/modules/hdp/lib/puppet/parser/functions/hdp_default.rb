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
module Puppet::Parser::Functions
  newfunction(:hdp_default, :type => :rvalue) do |args|
    args = function_hdp_args_as_array(args)
    scoped_var_name = args[0]
    var_name = scoped_var_name.split("/").last
    default = args[1]
    val = lookupvar("::#{var_name}")
    # To workaround string-boolean comparison issues,
    # ensure that we return boolean result if the default value
    # is also boolean
    if default == true or default == false # we expect boolean value as a result
      casted_val = (val == "true" or val == true) # converting to boolean
    else # default
      casted_val = val
    end
    function_hdp_is_empty(val) ? (default||"") : casted_val
  end
end


