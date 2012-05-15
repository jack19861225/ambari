function renderDeploySummary (deployInfo) {
  var deploySummary = "";

  for (var serviceName in deployInfo.services) {
    var serviceHasToBeRendered = false;
    var masterSummary = "";
    var propertySummary = "";

    if (deployInfo.services.hasOwnProperty( serviceName )) {

      var perServiceInfo = deployInfo.services[serviceName];

      var configElementName = serviceName;
      var configElementIdName = configElementName + 'Id';

      if (perServiceInfo.isEnabled == false) {
          continue;
      }

      for (var componentIndex in perServiceInfo.components) {
        if (!perServiceInfo.components[componentIndex].isMaster) {
          continue;
        }
        var component = perServiceInfo.components[componentIndex];
        serviceHasToBeRendered = true;
        masterSummary += '<label for=' + component.componentName + 'Id>' + component.displayName + '</label>' +
                         '<input type=text name=' + component.componentName + 'Name id=' + component.componentName + 'Id readonly=readonly value=\"' + component.hostName + '\">';
      }

      for (var mPropertiesKey in perServiceInfo.properties) {
        if (perServiceInfo.properties[mPropertiesKey].type == "NODISPLAY") {
          continue;
        }
        serviceHasToBeRendered = true;
        propertySummary += '<label for=' + mPropertiesKey  + 'Id>' + perServiceInfo.properties[mPropertiesKey].displayName + '</label>' +
                         '<input type=' + convertDisplayType(perServiceInfo.properties[mPropertiesKey].type) + ' name=' + mPropertiesKey + 'Name id=' + mPropertiesKey + 'Id readonly=readonly value=\"' + perServiceInfo.properties[mPropertiesKey].value +'\">';
      }
    }
    
    if (serviceHasToBeRendered) {
      deploySummary += '<fieldset>' + '<legend>' + perServiceInfo.displayName + '</legend>';
      deploySummary += masterSummary;
      deploySummary += propertySummary;
      deploySummary += '</fieldset><br/>';
    }
  }

  globalYui.log("Final HTML: " + globalYui.Lang.dump(deploySummary));

  globalYui.one("#deployDynamicRenderDivId").setContent( deploySummary );
  hideLoadingImg();
  globalYui.one("#deployCoreDivId").setStyle("display", "block");
}

var globalDeployInfo = null;

globalYui.one('#deploySubmitButtonId').on('click',function (e) {

    e.target.set('disabled', true);

    var deployRequestData = {};

    var url = "../php/frontend/deploy.php?clusterName="+globalDeployInfo.clusterName;
    var requestData = deployRequestData;
    var submitButton = e.target;
    var thisScreenId = "#deployCoreDivId";
    var nextScreenId = "#txnProgressCoreDivId";
    var nextScreenRenderFunction = renderDeployProgress;
    submitDataAndProgressToNextScreen(url, requestData, submitButton, thisScreenId, nextScreenId, nextScreenRenderFunction);
});


function renderDeploy (deployInfo) {
  globalDeployInfo = deployInfo;
  var inputUrl = "../php/frontend/fetchClusterServices.php?clusterName=" + deployInfo.clusterName + "&getConfigs=true&getComponents=true";
  executeStage(inputUrl, renderDeploySummary);
}