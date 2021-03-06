/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


var App = require('app');

App.WizardStep5View = Em.View.extend({

  templateName:require('templates/wizard/step5'),

  didInsertElement:function () {
    this.get('controller').loadStep();
  }

});

App.SelectHostView = Em.Select.extend({
  content:[],
  zId:null,
  selectedHost:null,
  componentName:null,
  attributeBindings:['disabled'],

  filterContent:function () {
    this.get('content').sort(function (a, b) {
      if (a.get('memory') == b.get('memory')) {
        if (a.get('cpu') == b.get('cpu')) {

//          try to compare as ipaddresses
          if (a.get('host_name').ip2long() && b.get('host_name').ip2long()) {
            return a.get('host_name').ip2long() - b.get('host_name').ip2long(); // hostname asc
          }

//          try to compare as strings
          if (a.get('host_name') > b.get('host_name')) {
            return 1;
          }

          if (b.get('host_name') > a.get('host_name')) {
            return -1;
          }

          return 0;
        }
        return b.get('cpu') - a.get('cpu'); // cores desc
      }

      return b.get('memory') - a.get('memory'); // ram desc
    });

  }.observes('content'),

  change:function () {
    this.get('controller').assignHostToMaster(this.get("componentName"), this.get("value"), this.get("zId"));
  },

  didInsertElement:function () {
    if (!this.get('controller.isReassignWizard')) {
      this.filterContent();
    }
    this.set("value", this.get("selectedHost"));
  }
});

App.AddControlView = Em.View.extend({
  componentName:null,
  tagName:"span",
  classNames:["badge", "badge-important"],
  template:Ember.Handlebars.compile('+'),

  click:function () {
    this.get('controller').addComponent(this.get('componentName'));
  }
});

App.RemoveControlView = Em.View.extend({
  zId:null,
  componentName:null,
  tagName:"span",
  classNames:["badge", "badge-important"],
  template:Ember.Handlebars.compile('-'),

  click:function () {
    this.get('controller').removeComponent(this.get('componentName'), this.get("zId"));
  }
});
