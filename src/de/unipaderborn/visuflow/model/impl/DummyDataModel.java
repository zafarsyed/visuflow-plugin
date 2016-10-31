package de.unipaderborn.visuflow.model.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.List;

import org.osgi.service.component.ComponentContext;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventAdmin;

import de.unipaderborn.visuflow.model.DataModel;
import de.unipaderborn.visuflow.model.VFClass;
import de.unipaderborn.visuflow.model.VFMethod;
import de.unipaderborn.visuflow.model.VFUnit;
import de.unipaderborn.visuflow.model.graph.ICFGStructure;
import de.unipaderborn.visuflow.model.graph.JimpleModelAnalysis;

public class DummyDataModel implements DataModel {
	private List<VFClass> jimpleClasses = new ArrayList<VFClass>();
	
	private VFClass selectedClass;
	private VFMethod selectedMethod;
	
	private List<VFMethod> selectedClassMethods;
	private List<VFUnit> selectedMethodUnits;
	
	private ICFGStructure icfg;

	public ICFGStructure getIcfg() {
		return icfg;
	}

	public VFClass getSelectedClass() {
		return selectedClass;
	}
	
	public List<VFMethod> getSelectedClassMethods() {
		return selectedClassMethods;
	}
	
	public VFMethod getSelectedMethod() {
		return selectedMethod;
	}

	public List<VFUnit> getSelectedMethodUnits() {
		return selectedMethodUnits;
	}

	public void setSelectedClass(VFClass selectedClass) {
		this.selectedClass = selectedClass;
		this.selectedMethod = this.selectedClass.getMethods().get(0);
		this.selectedClassMethods = this.selectedClass.getMethods();
		this.populateUnits();
	}
	
	public void setSelectedMethod(VFMethod selectedMethod)
	{
		this.selectedMethod = selectedMethod;
		this.populateUnits();
		
		Dictionary<String, Object> properties = new Hashtable<String, Object>();
		properties.put("selectedMethod", selectedMethod);
		properties.put("selectedMethodUnits", selectedMethodUnits);
		Event modelChanged = new Event(DataModel.EA_TOPIC_DATA_SELECTION, properties);
		eventAdmin.postEvent(modelChanged);
	}

	private void populateUnits() {
		this.selectedMethodUnits = this.selectedMethod.getUnits();
	}

	private EventAdmin eventAdmin;
	
	@Override
	public List<VFClass> listClasses() {
		return jimpleClasses;
	}

	@Override
	public List<VFMethod> listMethods(VFClass vfClass) {
		List<VFMethod> methods = Collections.emptyList();
		for (VFClass current : jimpleClasses) {
			if(current == vfClass) {
				methods = vfClass.getMethods();
			}
		}
		return methods;
	}

	@Override
	public List<VFUnit> listUnits(VFMethod vfMethod) {
		List<VFUnit> units = Collections.emptyList();
		for (VFClass currentClass : jimpleClasses) {
			for (VFMethod currentMethod : currentClass.getMethods()) {
				if(currentMethod == vfMethod) {
					units = vfMethod.getUnits();
				}
			}
		}
		return units;
	}
	
	public void setEventAdmin(EventAdmin eventAdmin) {
		this.eventAdmin = eventAdmin;
	}
	
	protected void activate(ComponentContext context)
    {
		this.icfg = new ICFGStructure();
		JimpleModelAnalysis analysis = new JimpleModelAnalysis();
		analysis.createICFG(this.icfg, jimpleClasses);
		this.setSelectedClass(jimpleClasses.get(0));
		System.out.println(this.icfg.listEdges.size());
		
		Dictionary<String, Object> properties = new Hashtable<String, Object>();
		properties.put("model", jimpleClasses);
		Event modelChanged = new Event(DataModel.EA_TOPIC_DATA_MODEL_CHANGED, properties);
		eventAdmin.postEvent(modelChanged);
    }

    protected void deactivate(ComponentContext context)
    {
    	// noop
    }

	@Override
	public void setClassList(List<VFClass> classList) {
		// TODO Auto-generated method stub
		
	}

}
