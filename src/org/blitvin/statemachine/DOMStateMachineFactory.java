/*
 * (C) Copyright Boris Litvin 2014, 2015
 * This file is part of FSM4Java library.
 *
 *  FSM4Java is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   FSM4Java is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with FSM4Java  If not, see <http://www.gnu.org/licenses/>.
 */
package org.blitvin.statemachine;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Source;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * DOMStateMachineFactory is factory constructing instances of StateMachine
 * from description supplied by xml file. The file should conform to state_machines.xsd specification.
 * File name can be supplied explicitly in constructor argument, resetFactory method argument or, for 
 * default file name , by property org.blitvin.statemachine.DOMStateMachineFactoryImplementation.defaultXmlFileName
 * @author blitvin
 *
 */
public class DOMStateMachineFactory extends	StateMachineFactory {
	
	
	/* xml tags */
	static final String STATE_MACHINE_TAG="stateMachine";
	static final String ELEMENT_NAME_TAG ="name";
	static final String IMPL_CLASS_TAG="class";
	static final String TRANSITION_TAG = "transition";
	static final String IS_INITIAL_STATE_TAG="isInitial";
	static final String IS_FINAL_STATE_TAG="isFinal";
	static final String ON_EVENT_TAG="event";
	static final String EVENT_TYPE_IMPL_CLASS_TAG="eventTypeClass";
	static final String DEFAULT_TRANSITION="other_events_transition";
	
	protected static final String XSD_FILE = "state_machines.xsd";
	public static final String DEFAULT_XML_FILE="empty.xml";
	public static final String DEFAULT_XML_FILE_PROPERTY = "org.blitvin.statemachine.DOMStateMachineFactoryImplementation.defaultXmlFileName";
	
	/**
	 * Class used for construction of state if class name is not specified explicitely
	 */
	public static final String DEFAULT_STATE_CLASS="org.blitvin.statemachine.State";
	/**
	 * name of default class used for transitions of the state machine 
	 */
	public static final String DEFAULT_TRANSITION_CLASS="org.blitvin.statemachine.SimpleTransition";
	/**
	 * name of state machine default class
	 */
	public static final String DEFAULT_STATE_MACHINE_CLASS = "org.blitvin.statemachine.SimpleStateMachine";
	
	@SuppressWarnings("rawtypes")
	protected static final Class[] STATE_CONSTUCTOR_PARAMS={String.class,Boolean.class};
	@SuppressWarnings("rawtypes")
	protected static final Class[] VALUE_OF_PARAMS = {String.class};
	@SuppressWarnings("rawtypes")
	protected static final Class[] STATE_MACHINE_CONSTRUCTOR_PARAMS={Map.class,State.class};
	/* list of parsed state machine specifications */
	protected HashMap<String,Node> stateMachineSpecs;
	
	protected static final HashSet<String> standardStateTags = new HashSet<>();
	static {
		standardStateTags.add(IS_FINAL_STATE_TAG);
		standardStateTags.add(IS_INITIAL_STATE_TAG);
		standardStateTags.add(IMPL_CLASS_TAG);
		standardStateTags.add(ELEMENT_NAME_TAG);
	}
	
	protected static final HashSet<String> standardTransitionTags = new HashSet<>();
	static {
		standardTransitionTags.add(ELEMENT_NAME_TAG);
		standardTransitionTags.add(IMPL_CLASS_TAG);
		standardTransitionTags.add(ON_EVENT_TAG);
	}
	/**
	 * default constructor, xml file name is deduced from property @see {@link #DEFAULT_XML_FILE_PROPERTY}.
	 * File is searched by class loader i.e. in most cases in CLASSPATH 
	 * @throws InvalidFactoryImplementation
	 */
	public DOMStateMachineFactory() throws InvalidFactoryImplementation{
		String xmlFileName =System.getProperty(DEFAULT_XML_FILE_PROPERTY,DEFAULT_XML_FILE);
		parseXml(xmlFileName);
	}
	
	/**
	 * constructor with explicit file name argument
	 * @param xmlFileName name of XML file to parse
	 * @throws InvalidFactoryImplementation
	 */
	public DOMStateMachineFactory(String xmlFileName) throws InvalidFactoryImplementation{
		parseXml(xmlFileName);
	}
	
	/**
	 * this method discards previous parsed XML file and loads new one
	 * @param xmlFileName name of new XML file to parse
	 * @throws InvalidFactoryImplementation
	 */
	public void resetFactory(String xmlFileName) throws InvalidFactoryImplementation{
		parseXml(xmlFileName);
	}
	
	private void parseXml(String xmlFileName) throws InvalidFactoryImplementation{
		 try {
			DocumentBuilder dBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
			Document stateMachines =dBuilder.parse(ClassLoader.getSystemResourceAsStream(xmlFileName));
			SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);

		    // load a WXS schema, represented by a Schema instance
		    Source schemaFile = new StreamSource(ClassLoader.getSystemResourceAsStream(XSD_FILE));
		    Schema schema = null;
			try {
				schema = factory.newSchema(schemaFile);
			} catch (SAXException e1) {
				throw new InvalidFactoryImplementation("can't parse xsd schema",e1);
			}
			Validator validator = schema.newValidator();
		    validator.validate(new DOMSource(stateMachines));
		    
		    stateMachines.getDocumentElement().normalize();
		    stateMachineSpecs = new HashMap<>();
			NodeList nList = stateMachines.getElementsByTagName(STATE_MACHINE_TAG);
			for (int idx =0 ; idx< nList.getLength(); ++idx)
				stateMachineSpecs.put(((Element)nList.item(idx)).getAttribute(ELEMENT_NAME_TAG), nList.item(idx));

		} catch (ParserConfigurationException e) {
			throw new InvalidFactoryImplementation("can't parse configuration", e);
		} catch (SAXException e) {
			throw new InvalidFactoryImplementation("invalid xml file", e);
		} catch (IOException e) {
			throw new InvalidFactoryImplementation("Error while reading xml or xsd file", e);
		}
	}

	/**
	 * Returns state object, without transitions, use buildTransition for this
	 * @param stateElement
	 * @return state object
	 * @throws BadStateMachineSpecification 
	 */
	@SuppressWarnings("rawtypes")
	private State buildState(Element stateElement) throws BadStateMachineSpecification{
		Class stateClass = getClass(stateElement, DEFAULT_STATE_CLASS,State.class);
		
			try {
				@SuppressWarnings("unchecked")
				Constructor c = stateClass.getConstructor(STATE_CONSTUCTOR_PARAMS);
				Object[] args = new Object[2];
				args[0] = stateElement.getAttribute(ELEMENT_NAME_TAG);
				args[1] =  stateElement.hasAttribute(IS_FINAL_STATE_TAG)?Boolean.valueOf(stateElement.getAttribute(IS_FINAL_STATE_TAG)):Boolean.FALSE;
				State s = (State) c.newInstance(args);
				return s;
			} catch (NoSuchMethodException|SecurityException|InstantiationException|IllegalAccessException|
					IllegalArgumentException|InvocationTargetException e) {
				throw new BadStateMachineSpecification("Exception during attempt to construct state "+stateElement.getAttribute(ELEMENT_NAME_TAG), e);
			} 
		
		
	}
	
	private void fillAttributes(Node n, @SuppressWarnings("rawtypes") StateMachineBuilder builder, HashSet<String> standards){
		NamedNodeMap map = n.getAttributes();
		for(int i =0 ; i < map.getLength(); ++i) {
			if (!standards.contains(map.item(i).getNodeName())){
				builder.addAttribute(map.item(i).getNodeName(), map.item(i).getNodeValue());
			}
		}
	}
	
	@SuppressWarnings("rawtypes")
	private Object getEventTypeConst(Class eventTypeClass, String value) throws BadStateMachineSpecification{
		Object[] mvargs =  new Object[1];
		mvargs[0] = value;
		try {
			@SuppressWarnings("unchecked")
			Method vo = eventTypeClass.getMethod("valueOf", VALUE_OF_PARAMS);
			return vo.invoke(null, mvargs);
		} catch (IllegalAccessException | IllegalArgumentException
				| InvocationTargetException | NoSuchMethodException | SecurityException e) {
			throw new BadStateMachineSpecification(value +" is not a valid event type constant", e);
		}
	}
	
	/**
	 * returns instance of state machine
	 * each invocation creates machine with distinct set of states and transitions (no sharing of
	 * states among instances of machine created from the same specification)
	 * @param machineName name of state machine as specified by name attribute of StateMachine entry
	 * @param constructorArguments object containing additional parameters to be passed to state machine's constructor
	 * @return new instance of the machine constructed according to XML file's specifications
	 * @throws BadStateMachineSpecification if construction failed for any reason
	 */
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public StateMachine getStateMachine(String machineName, Object construcorArguments) throws BadStateMachineSpecification {
		Node stateMachineNode = stateMachineSpecs.get(machineName);
		if (stateMachineNode == null)
			throw new BadStateMachineSpecification("Unknown state machine name:"+machineName);
		Element stateMachineElem = (Element)stateMachineNode;
		
		/* event type class represents enum type of state machine alphabet*/
		Class eventTypeClass = null;
		
		try {
			eventTypeClass = Class.forName(stateMachineElem.getAttribute(EVENT_TYPE_IMPL_CLASS_TAG));
		}catch (ClassNotFoundException e) {
			throw new BadStateMachineSpecification("Event type class "+ EVENT_TYPE_IMPL_CLASS_TAG+ " not found", e);
		}
		/* event type expected to be enum */
		if (!eventTypeClass.isEnum())
			throw new BadStateMachineSpecification("Expecting enum class name in attribute "+EVENT_TYPE_IMPL_CLASS_TAG);
		
		StateMachineBuilder builder = new StateMachineBuilder<>(machineName,
				getClass((Element)stateMachineNode,DEFAULT_STATE_MACHINE_CLASS,SimpleStateMachine.class));
		
		NodeList stateNodes =stateMachineElem.getChildNodes();
		for(int i = 0 ; i < stateNodes.getLength(); ++i) {
			if (stateNodes.item(i).getNodeType() != Node.ELEMENT_NODE)
				continue;
			Element stateElem = (Element) stateNodes.item(i);
			//if (stateElem.hasAttribute(IMPL_CLASS_TAG))
			
			State s = buildState(stateElem);
			builder.addState(s);
			if (stateElem.hasAttribute(IS_INITIAL_STATE_TAG) && Boolean.valueOf(stateElem.getAttribute(IS_INITIAL_STATE_TAG))){
				builder.markStateAsInitial();
			}
			fillAttributes(stateElem, builder, standardStateTags);
			NodeList transitionNodes = stateElem.getChildNodes();
			/* now construct transitions for current state */
			for(int j = 0 ; j < transitionNodes.getLength() ; ++j) {
				Node transitionNode = transitionNodes.item(j);
				if ( transitionNode.getNodeType()== Node.ELEMENT_NODE) {
					Class transitionClass = getClass((Element) transitionNode,DEFAULT_TRANSITION_CLASS,Transition.class);
					try {
						Transition transition = (Transition) transitionClass.newInstance();
						if (transitionNode.getNodeName().equals(DEFAULT_TRANSITION)) {
							builder.addDefaultTransition(transition);
						}
						else {
							Object eventType = getEventTypeConst(eventTypeClass, 
									((Element)transitionNode).getAttribute(ON_EVENT_TAG));
							builder.addTransition((Enum)eventType, transition);
						}
						fillAttributes(transitionNode, builder, standardTransitionTags);
					}
					catch (InstantiationException | IllegalAccessException e) {
						throw new BadStateMachineSpecification("Can't create transition ",e);
					}
				}
			}
			
		}
		if (construcorArguments == null)
			return builder.build();
		else
			return builder.build(construcorArguments);
		
	}
	
	/**
	 * returns instance of state machine
	 * each invocation creates machine with distinct set of states and transitions (no sharing of
	 * states among instances of machine created from the same specification)
	 * @param machineName name of state machine as specified by name attribute of StateMachine entry
	 * @return new instance of the machine constructed according to XML file's specifications
	 * @throws BadStateMachineSpecification if construction failed for any reason
	 */
	@SuppressWarnings({ "rawtypes" })
	@Override
	public StateMachine getStateMachine(String machineName) throws BadStateMachineSpecification {
		return getStateMachine(machineName,null);
	}
	@SuppressWarnings({ "rawtypes", "unchecked" })
	private Class getClass(Element theNode, String defaultClassName,Class template)
			throws BadStateMachineSpecification {
		Class retVal = null;
		String className = null;
		if (theNode.hasAttribute(IMPL_CLASS_TAG))
			className = theNode.getAttribute(IMPL_CLASS_TAG);
		else
			className = defaultClassName;
		
		try {
			retVal = Class.forName(className);
			if (!template.isAssignableFrom(retVal))
				throw new BadStateMachineSpecification(className + " is not inherited from "+template.getCanonicalName() );
		} catch (ClassNotFoundException e) {
			throw new BadStateMachineSpecification("class not found", e);
		}


			return retVal;
	}
}
