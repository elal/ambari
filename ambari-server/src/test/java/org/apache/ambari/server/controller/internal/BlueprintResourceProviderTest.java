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

package org.apache.ambari.server.controller.internal;

import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.createNiceMock;
import static org.easymock.EasyMock.createStrictMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.reset;
import static org.easymock.EasyMock.verify;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.ambari.server.api.services.AmbariMetaInfo;
import org.apache.ambari.server.controller.AmbariManagementController;
import org.apache.ambari.server.controller.internal.BlueprintResourceProvider.BlueprintConfigPopulationStrategy;
import org.apache.ambari.server.controller.internal.BlueprintResourceProvider.BlueprintConfigPopulationStrategyV1;
import org.apache.ambari.server.controller.internal.BlueprintResourceProvider.BlueprintConfigPopulationStrategyV2;
import org.apache.ambari.server.controller.predicate.EqualsPredicate;
import org.apache.ambari.server.controller.spi.NoSuchParentResourceException;
import org.apache.ambari.server.controller.spi.NoSuchResourceException;
import org.apache.ambari.server.controller.spi.Predicate;
import org.apache.ambari.server.controller.spi.Request;
import org.apache.ambari.server.controller.spi.Resource;
import org.apache.ambari.server.controller.spi.ResourceProvider;
import org.apache.ambari.server.controller.spi.SystemException;
import org.apache.ambari.server.controller.spi.UnsupportedPropertyException;
import org.apache.ambari.server.controller.utilities.PropertyHelper;
import org.apache.ambari.server.orm.dao.BlueprintDAO;
import org.apache.ambari.server.orm.dao.StackDAO;
import org.apache.ambari.server.orm.entities.BlueprintConfigEntity;
import org.apache.ambari.server.orm.entities.BlueprintConfiguration;
import org.apache.ambari.server.orm.entities.BlueprintEntity;
import org.apache.ambari.server.orm.entities.HostGroupComponentEntity;
import org.apache.ambari.server.orm.entities.HostGroupConfigEntity;
import org.apache.ambari.server.orm.entities.HostGroupEntity;
import org.apache.ambari.server.orm.entities.StackEntity;
import org.apache.ambari.server.utils.StageUtils;
import org.apache.ambari.server.topology.Blueprint;
import org.apache.ambari.server.topology.BlueprintFactory;
import org.apache.ambari.server.topology.InvalidTopologyException;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import com.google.gson.Gson;

/**
 * BlueprintResourceProvider unit tests.
 */
@SuppressWarnings("unchecked")
public class BlueprintResourceProviderTest {

  private static String BLUEPRINT_NAME = "test-blueprint";

  @Rule
  public ExpectedException expectedException = ExpectedException.none();

  private final static BlueprintDAO dao = createStrictMock(BlueprintDAO.class);
  private final static StackDAO stackDAO = createNiceMock(StackDAO.class);
  private final static BlueprintEntity entity = createStrictMock(BlueprintEntity.class);
  private final static Blueprint blueprint = createMock(Blueprint.class);
  private final static AmbariMetaInfo metaInfo = createMock(AmbariMetaInfo.class);
  private final static BlueprintFactory blueprintFactory = createMock(BlueprintFactory.class);
  private final static BlueprintResourceProvider provider = createProvider();
  private final static Gson gson = new Gson();


  @BeforeClass
  public static void initClass() {
    BlueprintResourceProvider.init(blueprintFactory, dao, gson);

    StackEntity stackEntity = new StackEntity();
    stackEntity.setStackName("test-stack-name");
    stackEntity.setStackVersion("test-stack-version");

    expect(
        stackDAO.find(anyObject(String.class),
            anyObject(String.class))).andReturn(stackEntity).anyTimes();

    replay(stackDAO);

  }

  @Before
  public void resetGlobalMocks() {
    reset(dao, metaInfo, blueprintFactory, blueprint, entity);
  }

  @Test
  public void testCreateResources() throws Exception {

    AmbariManagementController managementController = createMock(AmbariManagementController.class);
    Request request = createMock(Request.class);
    //Capture<Set<StackServiceRequest>> stackServiceRequestCapture = new Capture<Set<StackServiceRequest>>();

//    Map<String, ServiceInfo> services = new HashMap<String, ServiceInfo>();
//    ServiceInfo service = new ServiceInfo();
//    service.setName("test-service");
//    services.put("test-service", service);

//    List<ComponentInfo> serviceComponents = new ArrayList<ComponentInfo>();
//    ComponentInfo component1 = new ComponentInfo();
//    component1.setName("component1");
//    ComponentInfo component2 = new ComponentInfo();
//    component2.setName("component2");
//    serviceComponents.add(component1);
//    serviceComponents.add(component2);

    Set<Map<String, Object>> setProperties = getBlueprintTestProperties();

    // set expectations
    expect(blueprintFactory.createBlueprint(setProperties.iterator().next())).andReturn(blueprint).once();
    blueprint.validateRequiredProperties();
    blueprint.validateTopology();
    expect(blueprint.toEntity()).andReturn(entity);
    expect(blueprint.getName()).andReturn(BLUEPRINT_NAME).atLeastOnce();
    expect(request.getProperties()).andReturn(setProperties);
    expect(request.getRequestInfoProperties()).andReturn(Collections.<String, String>emptyMap());
    expect(dao.findByName(BLUEPRINT_NAME)).andReturn(null);
//    expect(metaInfo.getServices("test-stack-name", "test-stack-version")).andReturn(services).anyTimes();
//    expect(metaInfo.getComponentsByService("test-stack-name", "test-stack-version", "test-service")).
//        andReturn(serviceComponents).anyTimes();
//    expect(metaInfo.getComponentToService("test-stack-name", "test-stack-version", "component1")).
//        andReturn("test-service").anyTimes();
//    expect(metaInfo.getComponentToService("test-stack-name", "test-stack-version", "component2")).
//        andReturn("test-service").anyTimes();
//    expect(metaInfo.getService("test-stack-name", "test-stack-version", "test-service")).andReturn(service).anyTimes();
    dao.create(entity);

    replay(dao, entity, metaInfo, blueprintFactory, blueprint, request, managementController);
    // end expectations

    ResourceProvider provider = AbstractControllerResourceProvider.getResourceProvider(
        Resource.Type.Blueprint,
        PropertyHelper.getPropertyIds(Resource.Type.Blueprint),
        PropertyHelper.getKeyPropertyIds(Resource.Type.Blueprint),
        managementController);

    AbstractResourceProviderTest.TestObserver observer = new AbstractResourceProviderTest.TestObserver();
    ((ObservableResourceProvider)provider).addObserver(observer);

    provider.createResources(request);

    ResourceProviderEvent lastEvent = observer.getLastEvent();
    assertNotNull(lastEvent);
    assertEquals(Resource.Type.Blueprint, lastEvent.getResourceType());
    assertEquals(ResourceProviderEvent.Type.Create, lastEvent.getType());
    assertEquals(request, lastEvent.getRequest());
    assertNull(lastEvent.getPredicate());

    //validateEntity(entityCapture.getValue(), false);

    verify(dao, entity, blueprintFactory, metaInfo, request, managementController);
  }

  @Test
  public void testCreateResources_NoValidation() throws Exception {

    AmbariManagementController managementController = createMock(AmbariManagementController.class);
    Request request = createMock(Request.class);


    Set<Map<String, Object>> setProperties = getBlueprintTestProperties();

    // set expectations
    expect(blueprintFactory.createBlueprint(setProperties.iterator().next())).andReturn(blueprint).once();
    blueprint.validateRequiredProperties();
    expect(blueprint.toEntity()).andReturn(entity);
    expect(blueprint.getName()).andReturn(BLUEPRINT_NAME).atLeastOnce();
    expect(request.getProperties()).andReturn(setProperties);
    expect(request.getRequestInfoProperties()).andReturn(Collections.<String, String>singletonMap("validate_topology", "false"));
    expect(dao.findByName(BLUEPRINT_NAME)).andReturn(null);
    dao.create(entity);

    replay(dao, entity, metaInfo, blueprintFactory, blueprint, request, managementController);
    // end expectations

    ResourceProvider provider = AbstractControllerResourceProvider.getResourceProvider(
        Resource.Type.Blueprint,
        PropertyHelper.getPropertyIds(Resource.Type.Blueprint),
        PropertyHelper.getKeyPropertyIds(Resource.Type.Blueprint),
        managementController);

    AbstractResourceProviderTest.TestObserver observer = new AbstractResourceProviderTest.TestObserver();
    ((ObservableResourceProvider)provider).addObserver(observer);

    provider.createResources(request);

    ResourceProviderEvent lastEvent = observer.getLastEvent();
    assertNotNull(lastEvent);
    assertEquals(Resource.Type.Blueprint, lastEvent.getResourceType());
    assertEquals(ResourceProviderEvent.Type.Create, lastEvent.getType());
    assertEquals(request, lastEvent.getRequest());
    assertNull(lastEvent.getPredicate());

    verify(dao, entity, blueprintFactory, metaInfo, request, managementController);
  }

  @Test
  public void testCreateResources_TopologyValidationFails() throws Exception {

    Request request = createMock(Request.class);
    Set<Map<String, Object>> setProperties = getBlueprintTestProperties();

    // set expectations
    expect(blueprintFactory.createBlueprint(setProperties.iterator().next())).andReturn(blueprint).once();
    blueprint.validateRequiredProperties();
    expect(blueprint.getName()).andReturn(BLUEPRINT_NAME).atLeastOnce();
    blueprint.validateTopology();
    expectLastCall().andThrow(new InvalidTopologyException("test"));

    expect(request.getProperties()).andReturn(setProperties);
    expect(request.getRequestInfoProperties()).andReturn(Collections.<String, String>emptyMap());
    expect(dao.findByName(BLUEPRINT_NAME)).andReturn(null);

    replay(dao, entity, metaInfo, blueprintFactory, blueprint, request);
    // end expectations

    ResourceProvider provider = AbstractControllerResourceProvider.getResourceProvider(
        Resource.Type.Blueprint,
        PropertyHelper.getPropertyIds(Resource.Type.Blueprint),
        PropertyHelper.getKeyPropertyIds(Resource.Type.Blueprint),
        createMock(AmbariManagementController.class));

    AbstractResourceProviderTest.TestObserver observer = new AbstractResourceProviderTest.TestObserver();
    ((ObservableResourceProvider)provider).addObserver(observer);

    try {
      provider.createResources(request);
      fail("Expected exception due to topology validation error");
    } catch (IllegalArgumentException e) {
      // expected
    }

    verify(dao, entity, blueprintFactory, metaInfo, request);
  }


  @Test
  public void testCreateResources_withConfiguration() throws Exception {

    Set<Map<String, Object>> setProperties = getBlueprintTestProperties();
    setConfigurationProperties(setProperties);
    AmbariManagementController managementController = createMock(AmbariManagementController.class);
//    Capture<Set<StackServiceRequest>> stackServiceRequestCapture = new Capture<Set<StackServiceRequest>>();
    Request request = createMock(Request.class);

//    Map<String, ServiceInfo> services = new HashMap<String, ServiceInfo>();
//    ServiceInfo service = new ServiceInfo();
//    service.setName("test-service");
//    services.put("test-service", service);
//
//    List<ComponentInfo> serviceComponents = new ArrayList<ComponentInfo>();
//    ComponentInfo component1 = new ComponentInfo();
//    component1.setName("component1");
//    ComponentInfo component2 = new ComponentInfo();
//    component2.setName("component2");
//    serviceComponents.add(component1);
//    serviceComponents.add(component2);


    // set expectations
    expect(blueprintFactory.createBlueprint(setProperties.iterator().next())).andReturn(blueprint).once();
    blueprint.validateRequiredProperties();
    blueprint.validateTopology();
    expect(blueprint.toEntity()).andReturn(entity);
    expect(blueprint.getName()).andReturn(BLUEPRINT_NAME).atLeastOnce();
    expect(request.getProperties()).andReturn(setProperties);
    expect(request.getRequestInfoProperties()).andReturn(Collections.<String, String>emptyMap());
    expect(dao.findByName(BLUEPRINT_NAME)).andReturn(null);
    dao.create(entity);

    replay(dao, entity, metaInfo, blueprintFactory, blueprint, request, managementController);
    // end expectations

    ResourceProvider provider = AbstractControllerResourceProvider.getResourceProvider(
        Resource.Type.Blueprint,
        PropertyHelper.getPropertyIds(Resource.Type.Blueprint),
        PropertyHelper.getKeyPropertyIds(Resource.Type.Blueprint),
        managementController);

    AbstractResourceProviderTest.TestObserver observer = new AbstractResourceProviderTest.TestObserver();
    ((ObservableResourceProvider)provider).addObserver(observer);

    provider.createResources(request);

    ResourceProviderEvent lastEvent = observer.getLastEvent();
    assertNotNull(lastEvent);
    assertEquals(Resource.Type.Blueprint, lastEvent.getResourceType());
    assertEquals(ResourceProviderEvent.Type.Create, lastEvent.getType());
    assertEquals(request, lastEvent.getRequest());
    assertNull(lastEvent.getPredicate());

    verify(dao, entity, blueprintFactory, metaInfo, request, managementController);
  }

  @Test
  public void testCreateResource_BlueprintFactoryThrowsException() throws Exception
  {
    Request request = createMock(Request.class);

    Set<Map<String, Object>> setProperties = getBlueprintTestProperties();
    setProperties.iterator().next().remove(BlueprintResourceProvider.HOST_GROUP_PROPERTY_ID);

    // set expectations
    expect(blueprintFactory.createBlueprint(setProperties.iterator().next())).andThrow(
        new IllegalArgumentException("Blueprint name must be provided"));
    expect(request.getProperties()).andReturn(setProperties);
    expect(request.getRequestInfoProperties()).andReturn(Collections.<String, String>emptyMap());

    replay(dao, entity, metaInfo, blueprintFactory, blueprint, request);
    // end expectations

    try {
      provider.createResources(request);
      fail("Exception expected");
    } catch (IllegalArgumentException e) {
      // expected
    }
    verify(dao, entity, blueprintFactory, metaInfo, request);
  }


  @Test
  public void testGetResourcesNoPredicate() throws SystemException, UnsupportedPropertyException,
                                                   NoSuchParentResourceException, NoSuchResourceException {
    Request request = createNiceMock(Request.class);

    BlueprintEntity entity = createEntity(getBlueprintTestProperties().iterator().next());

    List<BlueprintEntity> results = new ArrayList<BlueprintEntity>();
    results.add(entity);

    // set expectations
    expect(dao.findAll()).andReturn(results);
    replay(dao, request);

    Set<Resource> setResults = provider.getResources(request, null);
    assertEquals(1, setResults.size());

    verify(dao);
    validateResource(setResults.iterator().next(), false);
  }

  @Test
  public void testGetResourcesNoPredicate_withConfiguration() throws SystemException, UnsupportedPropertyException,
      NoSuchParentResourceException, NoSuchResourceException {
    Request request = createNiceMock(Request.class);

    Set<Map<String, Object>> testProperties = getBlueprintTestProperties();
    setConfigurationProperties(testProperties);
    BlueprintEntity entity = createEntity(testProperties.iterator().next());

    List<BlueprintEntity> results = new ArrayList<BlueprintEntity>();
    results.add(entity);

    // set expectations
    expect(dao.findAll()).andReturn(results);
    replay(dao, request);

    Set<Resource> setResults = provider.getResources(request, null);
    assertEquals(1, setResults.size());

    verify(dao);
    validateResource(setResults.iterator().next(), true);
  }


  @Test
  public void testDeleteResources() throws SystemException, UnsupportedPropertyException,
                                           NoSuchParentResourceException, NoSuchResourceException {

    BlueprintEntity blueprintEntity = createEntity(getBlueprintTestProperties().iterator().next());

    // set expectations
    expect(dao.findByName(BLUEPRINT_NAME)).andReturn(blueprintEntity);
    dao.removeByName(blueprintEntity.getBlueprintName());
    expectLastCall();
    replay(dao);

    Predicate predicate = new EqualsPredicate<String>(
        BlueprintResourceProvider.BLUEPRINT_NAME_PROPERTY_ID, BLUEPRINT_NAME);

    AbstractResourceProviderTest.TestObserver observer = new AbstractResourceProviderTest.TestObserver();
    provider.addObserver(observer);

    provider.deleteResources(predicate);

    ResourceProviderEvent lastEvent = observer.getLastEvent();
    assertNotNull(lastEvent);
    assertEquals(Resource.Type.Blueprint, lastEvent.getResourceType());
    assertEquals(ResourceProviderEvent.Type.Delete, lastEvent.getType());
    assertNotNull(lastEvent.getPredicate());

    verify(dao);
  }

  public static Set<Map<String, Object>> getBlueprintTestProperties() {
    Map<String, String> mapHostGroupComponentProperties = new HashMap<String, String>();
    mapHostGroupComponentProperties.put(BlueprintResourceProvider.COMPONENT_NAME_PROPERTY_ID, "component1");

    Map<String, String> mapHostGroupComponentProperties2 = new HashMap<String, String>();
    mapHostGroupComponentProperties2.put(BlueprintResourceProvider.COMPONENT_NAME_PROPERTY_ID, "component2");

    Set<Map<String, String>> setComponentProperties = new HashSet<Map<String, String>>();
    setComponentProperties.add(mapHostGroupComponentProperties);
    setComponentProperties.add(mapHostGroupComponentProperties2);

    Set<Map<String, String>> setComponentProperties2 = new HashSet<Map<String, String>>();
    setComponentProperties2.add(mapHostGroupComponentProperties);

    Map<String, Object> mapHostGroupProperties = new HashMap<String, Object>();
    mapHostGroupProperties.put(BlueprintResourceProvider.HOST_GROUP_NAME_PROPERTY_ID, "group1");
    mapHostGroupProperties.put(BlueprintResourceProvider.HOST_GROUP_CARDINALITY_PROPERTY_ID, "1");
    mapHostGroupProperties.put(BlueprintResourceProvider.COMPONENT_PROPERTY_ID, setComponentProperties);

    Map<String, Object> mapHostGroupProperties2 = new HashMap<String, Object>();
    mapHostGroupProperties2.put(BlueprintResourceProvider.HOST_GROUP_NAME_PROPERTY_ID, "group2");
    mapHostGroupProperties2.put(BlueprintResourceProvider.HOST_GROUP_CARDINALITY_PROPERTY_ID, "2");
    mapHostGroupProperties2.put(BlueprintResourceProvider.COMPONENT_PROPERTY_ID, setComponentProperties2);

    Set<Map<String, Object>> setHostGroupProperties = new HashSet<Map<String, Object>>();
    setHostGroupProperties.add(mapHostGroupProperties);
    setHostGroupProperties.add(mapHostGroupProperties2);

    Map<String, Object> mapProperties = new HashMap<String, Object>();
    mapProperties.put(BlueprintResourceProvider.BLUEPRINT_NAME_PROPERTY_ID, BLUEPRINT_NAME);
    mapProperties.put(BlueprintResourceProvider.STACK_NAME_PROPERTY_ID, "test-stack-name");
    mapProperties.put(BlueprintResourceProvider.STACK_VERSION_PROPERTY_ID, "test-stack-version");
    mapProperties.put(BlueprintResourceProvider.HOST_GROUP_PROPERTY_ID, setHostGroupProperties);

    return Collections.singleton(mapProperties);
  }

  public static void setConfigurationProperties(Set<Map<String, Object>> properties ) {
    Map<String, String> clusterProperties = new HashMap<String, String>();
    clusterProperties.put("core-site/properties/fs.trash.interval", "480");
    clusterProperties.put("core-site/properties/ipc.client.idlethreshold", "8500");
    clusterProperties.put("core-site/properties_attributes/final/ipc.client.idlethreshold", "true");

    // single entry in set which was created in getTestProperties
    Map<String, Object> mapProperties = properties.iterator().next();
    Set<Map<String, String>> configurations = new HashSet<Map<String, String>>();
    configurations.add(clusterProperties);
    mapProperties.put("configurations", configurations);

    Map<String, Object> hostGroupProperties = new HashMap<String, Object>();
    hostGroupProperties.put("core-site/my.custom.hg.property", "anything");

    Collection<Map<String, Object>> hostGroups = (Collection<Map<String, Object>>) mapProperties.get
        (BlueprintResourceProvider.HOST_GROUP_PROPERTY_ID);

    for (Map<String, Object> hostGroupProps : hostGroups) {
      if (hostGroupProps.get(BlueprintResourceProvider.HOST_GROUP_NAME_PROPERTY_ID).equals("group2")) {
        hostGroupProps.put("configurations", Collections.singleton(hostGroupProperties));
        break;
      }
    }
  }

  private void validateResource(Resource resource, boolean containsConfig) {
    assertEquals(BLUEPRINT_NAME, resource.getPropertyValue(BlueprintResourceProvider.BLUEPRINT_NAME_PROPERTY_ID));
    assertEquals("test-stack-name", resource.getPropertyValue(BlueprintResourceProvider.STACK_NAME_PROPERTY_ID));
    assertEquals("test-stack-version", resource.getPropertyValue(BlueprintResourceProvider.STACK_VERSION_PROPERTY_ID));

    Collection<Map<String, Object>> hostGroupProperties = (Collection<Map<String, Object>>)
        resource.getPropertyValue(BlueprintResourceProvider.HOST_GROUP_PROPERTY_ID);

    assertEquals(2, hostGroupProperties.size());
    for (Map<String, Object> hostGroupProps : hostGroupProperties) {
      String name = (String) hostGroupProps.get(BlueprintResourceProvider.HOST_GROUP_NAME_PROPERTY_ID);
      assertTrue(name.equals("group1") || name.equals("group2"));
      List<Map<String, String>> listComponents = (List<Map<String, String>>)
          hostGroupProps.get(BlueprintResourceProvider.COMPONENT_PROPERTY_ID);
      if (name.equals("group1")) {
        assertEquals("1", hostGroupProps.get(BlueprintResourceProvider.HOST_GROUP_CARDINALITY_PROPERTY_ID));
        assertEquals(2, listComponents.size());
        Map<String, String> mapComponent = listComponents.get(0);
        String componentName = mapComponent.get(BlueprintResourceProvider.COMPONENT_NAME_PROPERTY_ID);
        assertTrue(componentName.equals("component1") || componentName.equals("component2"));
        mapComponent = listComponents.get(1);
        String componentName2 = mapComponent.get(BlueprintResourceProvider.COMPONENT_NAME_PROPERTY_ID);
        assertFalse(componentName2.equals(componentName));
        assertTrue(componentName2.equals("component1") || componentName2.equals("component2"));
      } else if (name.equals("group2")) {
        assertEquals("2", hostGroupProps.get(BlueprintResourceProvider.HOST_GROUP_CARDINALITY_PROPERTY_ID));
        assertEquals(1, listComponents.size());
        Map<String, String> mapComponent = listComponents.get(0);
        String componentName = mapComponent.get(BlueprintResourceProvider.COMPONENT_NAME_PROPERTY_ID);
        assertEquals("component1", componentName);
      } else {
        fail("Unexpected host group name");
      }
    }

    if (containsConfig) {
      Collection<Map<String, Object>> blueprintConfigurations = (Collection<Map<String, Object>>)
          resource.getPropertyValue(BlueprintResourceProvider.CONFIGURATION_PROPERTY_ID);
      assertEquals(1, blueprintConfigurations.size());

      Map<String, Object> typeConfigs = blueprintConfigurations.iterator().next();
      assertEquals(1, typeConfigs.size());
      Map<String, Map<String, Object>> coreSiteConfig = (Map<String, Map<String, Object>>) typeConfigs.get("core-site");
      assertEquals(2, coreSiteConfig.size());
      assertTrue(coreSiteConfig.containsKey(BlueprintResourceProvider.PROPERTIES_PROPERTY_ID));
      Map<String, Object> properties = coreSiteConfig.get(BlueprintResourceProvider.PROPERTIES_PROPERTY_ID);
      assertNotNull(properties);
      assertEquals("480", properties.get("fs.trash.interval"));
      assertEquals("8500", properties.get("ipc.client.idlethreshold"));

      assertTrue(coreSiteConfig.containsKey(BlueprintResourceProvider.PROPERTIES_ATTRIBUTES_PROPERTY_ID));
      Map<String, Object> attributes = coreSiteConfig.get(BlueprintResourceProvider.PROPERTIES_ATTRIBUTES_PROPERTY_ID);
      assertNotNull(attributes);
      assertEquals(1, attributes.size());
      assertTrue(attributes.containsKey("final"));
      Map<String, String> finalAttrs = (Map<String, String>) attributes.get("final");
      assertEquals(1, finalAttrs.size());
      assertEquals("true", finalAttrs.get("ipc.client.idlethreshold"));
    }
  }

  private static BlueprintResourceProvider createProvider() {
    return new BlueprintResourceProvider(
        PropertyHelper.getPropertyIds(Resource.Type.Blueprint),
        PropertyHelper.getKeyPropertyIds(Resource.Type.Blueprint), null);
  }

  private BlueprintEntity createEntity(Map<String, Object> properties) {
    BlueprintEntity entity = new BlueprintEntity();
    entity.setBlueprintName((String) properties.get(BlueprintResourceProvider.BLUEPRINT_NAME_PROPERTY_ID));

    String stackName = (String) properties.get(BlueprintResourceProvider.STACK_NAME_PROPERTY_ID);
    String stackVersion = (String) properties.get(BlueprintResourceProvider.STACK_VERSION_PROPERTY_ID);
    StackEntity stackEntity = new StackEntity();
    stackEntity.setStackName(stackName);
    stackEntity.setStackVersion(stackVersion);

    entity.setStack(stackEntity);

    Set<Map<String, Object>> hostGroupProperties = (Set<Map<String, Object>>) properties.get(
        BlueprintResourceProvider.HOST_GROUP_PROPERTY_ID);

    Collection<HostGroupEntity> hostGroups = new ArrayList<HostGroupEntity>();
    for (Map<String, Object> groupProperties : hostGroupProperties) {
      HostGroupEntity hostGroup = new HostGroupEntity();
      hostGroups.add(hostGroup);
      hostGroup.setName((String) groupProperties.get(BlueprintResourceProvider.HOST_GROUP_NAME_PROPERTY_ID));
      hostGroup.setCardinality((String) groupProperties.get(BlueprintResourceProvider.HOST_GROUP_CARDINALITY_PROPERTY_ID));
      hostGroup.setConfigurations(new ArrayList<HostGroupConfigEntity>());

      Set<Map<String, String>> setComponentProperties = (Set<Map<String, String>>) groupProperties.get(
          BlueprintResourceProvider.COMPONENT_PROPERTY_ID);

      Collection<HostGroupComponentEntity> components = new ArrayList<HostGroupComponentEntity>();
      for (Map<String, String> compProperties : setComponentProperties) {
        HostGroupComponentEntity component = new HostGroupComponentEntity();
        components.add(component);
        component.setName(compProperties.get(BlueprintResourceProvider.COMPONENT_NAME_PROPERTY_ID));
      }
      hostGroup.setComponents(components);

    }
    entity.setHostGroups(hostGroups);


    Collection<Map<String, String>> configProperties = (Collection<Map<String, String>>) properties.get(
        BlueprintResourceProvider.CONFIGURATION_PROPERTY_ID);
    createProvider().createBlueprintConfigEntities(configProperties, entity);
    return entity;
  }

  @Test
  public void testPopulateConfigurationEntity_oldSchema() throws Exception {
    Map<String, String> configuration = new HashMap<String, String>();
    configuration.put("global/property1", "val1");
    configuration.put("global/property2", "val2");
    BlueprintConfiguration config = new BlueprintConfigEntity();

    provider.populateConfigurationEntity(configuration, config);

    assertNotNull(config.getConfigData());
    assertNotNull(config.getConfigAttributes());
    Map<?, ?> configData = StageUtils.getGson().fromJson(config.getConfigData(), Map.class);
    Map<?, Map<?, ?>> configAttrs = StageUtils.getGson().fromJson(config.getConfigAttributes(), Map.class);
    assertNotNull(configData);
    assertNotNull(configAttrs);
    assertEquals(2, configData.size());
    assertTrue(configData.containsKey("property1"));
    assertTrue(configData.containsKey("property2"));
    assertEquals("val1", configData.get("property1"));
    assertEquals("val2", configData.get("property2"));
    assertEquals(0, configAttrs.size());
  }

  @Test
  public void testPopulateConfigurationEntity_newSchema() throws Exception {
    Map<String, String> configuration = new HashMap<String, String>();
    configuration.put("global/properties/property1", "val1");
    configuration.put("global/properties/property2", "val2");
    configuration.put("global/properties_attributes/final/property1", "true");
    configuration.put("global/properties_attributes/final/property2", "false");
    configuration.put("global/properties_attributes/deletable/property1", "true");
    BlueprintConfiguration config = new BlueprintConfigEntity();

    provider.populateConfigurationEntity(configuration, config);

    assertNotNull(config.getConfigData());
    assertNotNull(config.getConfigAttributes());
    Map<?, ?> configData = StageUtils.getGson().fromJson(config.getConfigData(), Map.class);
    Map<?, Map<?, ?>> configAttrs = StageUtils.getGson().fromJson(config.getConfigAttributes(), Map.class);
    assertNotNull(configData);
    assertNotNull(configAttrs);
    assertEquals(2, configData.size());
    assertTrue(configData.containsKey("property1"));
    assertTrue(configData.containsKey("property2"));
    assertEquals("val1", configData.get("property1"));
    assertEquals("val2", configData.get("property2"));
    assertEquals(2, configAttrs.size());
    assertTrue(configAttrs.containsKey("final"));
    assertTrue(configAttrs.containsKey("deletable"));
    Map<?, ?> finalAttrs = configAttrs.get("final");
    assertNotNull(finalAttrs);
    assertEquals(2, finalAttrs.size());
    assertTrue(finalAttrs.containsKey("property1"));
    assertTrue(finalAttrs.containsKey("property2"));
    assertEquals("true", finalAttrs.get("property1"));
    assertEquals("false", finalAttrs.get("property2"));

    Map<?, ?> deletableAttrs = configAttrs.get("deletable");
    assertNotNull(deletableAttrs);
    assertEquals(1, deletableAttrs.size());
    assertTrue(deletableAttrs.containsKey("property1"));
    assertEquals("true", deletableAttrs.get("property1"));
  }

  @Test
  public void testPopulateConfigurationEntity_configIsNull() throws Exception {
    Map<String, String> configuration = null;
    BlueprintConfiguration config = new BlueprintConfigEntity();

    provider.populateConfigurationEntity(configuration, config);

    assertNotNull(config.getConfigAttributes());
    assertNotNull(config.getConfigData());
  }

  @Test
  public void testPopulateConfigurationEntity_configIsEmpty() throws Exception {
    Map<String, String> configuration = new HashMap<String, String>();
    BlueprintConfiguration config = new BlueprintConfigEntity();

    provider.populateConfigurationEntity(configuration, config);

    assertNotNull(config.getConfigAttributes());
    assertNotNull(config.getConfigData());
  }

  @Test
  public void testDecidePopulationStrategy_configIsEmpty() throws Exception {
    Map<String, String> configMap = new HashMap<String, String>();

    BlueprintConfigPopulationStrategy provisioner =
        provider.decidePopulationStrategy(configMap);

    assertNotNull(provisioner);
    assertTrue(provisioner instanceof BlueprintConfigPopulationStrategyV2);
  }

  @Test
  public void testDecidePopulationStrategy_configIsNull() throws Exception {
    Map<String, String> configMap = null;

    BlueprintConfigPopulationStrategy provisioner =
        provider.decidePopulationStrategy(configMap);

    assertNotNull(provisioner);
    assertTrue(provisioner instanceof BlueprintConfigPopulationStrategyV2);
  }

  @Test
  public void testDecidePopulationStrategy_withOldSchema() throws Exception {
    Map<String, String> configMap = new HashMap<String, String>();
    configMap.put("global/hive_database", "db");

    BlueprintConfigPopulationStrategy provisioner =
        provider.decidePopulationStrategy(configMap);

    assertNotNull(provisioner);
    assertTrue(provisioner instanceof BlueprintConfigPopulationStrategyV1);
  }

  @Test
  public void testDecidePopulationStrategy_withNewSchema_attributes() throws Exception {
    Map<String, String> configMap = new HashMap<String, String>();
    configMap.put("global/properties_attributes/final/foo_contact", "true");

    BlueprintConfigPopulationStrategy provisioner =
        provider.decidePopulationStrategy(configMap);

    assertNotNull(provisioner);
    assertTrue(provisioner instanceof BlueprintConfigPopulationStrategyV2);
  }

  @Test
  public void testDecidePopulationStrategy_withNewSchema_properties() throws Exception {
    Map<String, String> configMap = new HashMap<String, String>();
    configMap.put("global/properties/foo_contact", "foo@ffl.dsfds");

    BlueprintConfigPopulationStrategy provisioner =
        provider.decidePopulationStrategy(configMap);

    assertNotNull(provisioner);
    assertTrue(provisioner instanceof BlueprintConfigPopulationStrategyV2);
  }

  @Test
  public void testDecidePopulationStrategy_unsupportedSchema() throws Exception {
    Map<String, String> configMap = new HashMap<String, String>();
    configMap.put("global/properties/lot/foo_contact", "foo@ffl.dsfds");
    expectedException.expect(IllegalArgumentException.class);
    expectedException.expectMessage(BlueprintResourceProvider.SCHEMA_IS_NOT_SUPPORTED_MESSAGE);

    provider.decidePopulationStrategy(configMap);
  }

  @Test
  public void testPopulateConfigurationList() throws Exception {
    // attributes is null
    BlueprintConfiguration config1 = new BlueprintConfigEntity();
    config1.setType("type1");
    config1.setConfigData("{\"key1\":\"value1\"}");
    // attributes is empty
    BlueprintConfiguration config2 = new BlueprintConfigEntity();
    config2.setType("type2");
    config2.setConfigData("{\"key2\":\"value2\"}");
    config2.setConfigAttributes("{}");
    // attributes is provided
    BlueprintConfiguration config3 = new BlueprintConfigEntity();
    config3.setType("type3");
    config3.setConfigData("{\"key3\":\"value3\",\"key4\":\"value4\"}");
    config3.setConfigAttributes("{\"final\":{\"key3\":\"attrValue1\",\"key4\":\"attrValue2\"}}");

    List<Map<String, Map<String, Object>>> configs =
        provider.populateConfigurationList(Arrays.asList(config1, config2, config3));

    assertNotNull(configs);
    assertEquals(3, configs.size());
    Map<String, Map<String, Object>> configuration1 = configs.get(0);
    assertNotNull(configuration1);
    assertEquals(1, configuration1.size());
    assertTrue(configuration1.containsKey("type1"));
    Map<String, Object> typeConfig1 = configuration1.get("type1");
    assertNotNull(typeConfig1);
    assertEquals(1, typeConfig1.size());
    assertTrue(typeConfig1.containsKey(BlueprintResourceProvider.PROPERTIES_PROPERTY_ID));
    Map<String, String> confProperties1
        = (Map<String, String>) typeConfig1.get(BlueprintResourceProvider.PROPERTIES_PROPERTY_ID);
    assertNotNull(confProperties1);
    assertEquals(1, confProperties1.size());
    assertEquals("value1", confProperties1.get("key1"));

    Map<String, Map<String, Object>> configuration2 = configs.get(1);
    assertNotNull(configuration2);
    assertEquals(1, configuration2.size());
    assertTrue(configuration2.containsKey("type2"));
    Map<String, Object> typeConfig2 = configuration2.get("type2");
    assertNotNull(typeConfig2);
    assertEquals(1, typeConfig2.size());
    assertTrue(typeConfig2.containsKey(BlueprintResourceProvider.PROPERTIES_PROPERTY_ID));
    Map<String, String> confProperties2
        = (Map<String, String>) typeConfig2.get(BlueprintResourceProvider.PROPERTIES_PROPERTY_ID);
    assertNotNull(confProperties2);
    assertEquals(1, confProperties2.size());
    assertEquals("value2", confProperties2.get("key2"));

    Map<String, Map<String, Object>> configuration3 = configs.get(2);
    assertNotNull(configuration3);
    assertEquals(1, configuration3.size());
    assertTrue(configuration3.containsKey("type3"));
    Map<String, Object> typeConfig3 = configuration3.get("type3");
    assertNotNull(typeConfig3);
    assertEquals(2, typeConfig3.size());
    assertTrue(typeConfig3.containsKey(BlueprintResourceProvider.PROPERTIES_PROPERTY_ID));
    Map<String, String> confProperties3
        = (Map<String, String>) typeConfig3.get(BlueprintResourceProvider.PROPERTIES_PROPERTY_ID);
    assertNotNull(confProperties3);
    assertEquals(2, confProperties3.size());
    assertEquals("value3", confProperties3.get("key3"));
    assertEquals("value4", confProperties3.get("key4"));
    assertTrue(typeConfig3.containsKey(BlueprintResourceProvider.PROPERTIES_ATTRIBUTES_PROPERTY_ID));
    Map<String, Map<String, String>> confAttributes3
        = (Map<String, Map<String, String>>) typeConfig3.get(BlueprintResourceProvider.PROPERTIES_ATTRIBUTES_PROPERTY_ID);
    assertNotNull(confAttributes3);
    assertEquals(1, confAttributes3.size());
    assertTrue(confAttributes3.containsKey("final"));
    Map<String, String> finalAttrs = confAttributes3.get("final");
    assertEquals(2, finalAttrs.size());
    assertEquals("attrValue1", finalAttrs.get("key3"));
    assertEquals("attrValue2", finalAttrs.get("key4"));
  }
}

