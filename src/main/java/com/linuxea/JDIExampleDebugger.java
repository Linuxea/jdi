package com.linuxea;

import com.sun.jdi.AbsentInformationException;
import com.sun.jdi.Bootstrap;
import com.sun.jdi.ClassType;
import com.sun.jdi.IncompatibleThreadStateException;
import com.sun.jdi.LocalVariable;
import com.sun.jdi.Location;
import com.sun.jdi.StackFrame;
import com.sun.jdi.VMDisconnectedException;
import com.sun.jdi.Value;
import com.sun.jdi.VirtualMachine;
import com.sun.jdi.connect.Connector;
import com.sun.jdi.connect.Connector.Argument;
import com.sun.jdi.connect.LaunchingConnector;
import com.sun.jdi.event.BreakpointEvent;
import com.sun.jdi.event.ClassPrepareEvent;
import com.sun.jdi.event.Event;
import com.sun.jdi.event.EventSet;
import com.sun.jdi.event.LocatableEvent;
import com.sun.jdi.event.StepEvent;
import com.sun.jdi.request.BreakpointRequest;
import com.sun.jdi.request.ClassPrepareRequest;
import com.sun.jdi.request.StepRequest;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.Map;

public class JDIExampleDebugger<T> {

  private Class<T> debugClass;
  private int[] breakPointLines;

  public static void main(String[] args) throws IOException {

    JDIExampleDebugger<JDIExampleDebuggee> debuggerInstance = new JDIExampleDebugger<>();
    debuggerInstance.debugClass = JDIExampleDebuggee.class;
    debuggerInstance.breakPointLines = new int[]{6};
    VirtualMachine vm = null;
    try {
      vm = debuggerInstance.connectAndLaunchVM();
      debuggerInstance.enableClassPrepareRequest(vm);
      EventSet eventSet;
      while ((eventSet = vm.eventQueue().remove()) != null) {
        for (Event event : eventSet) {
//          System.out.println(event.getClass().getName());

          if (event instanceof ClassPrepareEvent) {
            debuggerInstance.setBreakPoints(vm, (ClassPrepareEvent) event);
          }
          if (event instanceof BreakpointEvent) {
            debuggerInstance.displayVariables((BreakpointEvent) event);
          }

          if (event instanceof BreakpointEvent) {
            debuggerInstance.enableStepRequest(vm, (BreakpointEvent) event);
          }

          if (event instanceof StepEvent) {
            debuggerInstance.displayVariables((StepEvent) event);
          }
          vm.resume();
        }
      }
    } catch (VMDisconnectedException e) {
      System.out.println("Virtual Machine is disconnected.");
    } catch (Exception e) {
      e.printStackTrace();
    }
    finally {
      InputStreamReader reader = new InputStreamReader(vm.process().getInputStream());
      OutputStreamWriter writer = new OutputStreamWriter(System.out);
      char[] buf = new char[512];
      int read = reader.read(buf);
      writer.write(buf, 0, read);
      writer.flush();
    }
  }

  public void enableStepRequest(VirtualMachine vm, BreakpointEvent event) {
    // enable step request for last break point
    if (event.location().toString().
        contains(debugClass.getName() + ":" + breakPointLines[breakPointLines.length - 1])) {
      StepRequest stepRequest = vm.eventRequestManager()
          .createStepRequest(event.thread(), StepRequest.STEP_LINE, StepRequest.STEP_OVER);
      stepRequest.enable();
    }
  }

  public VirtualMachine connectAndLaunchVM() throws Exception {

    LaunchingConnector launchingConnector = Bootstrap.virtualMachineManager()
        .defaultConnector();
    Map<String, Argument> arguments = launchingConnector.defaultArguments();
    arguments.get("main").setValue(debugClass.getName());

    //If you run this program as part of maven project
    // then classes will be compiled in target directory.
    //So you might need below additional env argument in code of JDIExampleDebuggee.
    Connector.Argument options = arguments.get("options");
    options.setValue("-cp \"C:\\Users\\Linux\\Desktop\\code\\jdi\\target\\classes\"");

    return launchingConnector.launch(arguments);
  }

  public void enableClassPrepareRequest(VirtualMachine vm) {
    ClassPrepareRequest classPrepareRequest = vm.eventRequestManager().createClassPrepareRequest();
    classPrepareRequest.addClassFilter(debugClass.getName());
    classPrepareRequest.enable();
  }

  public void setBreakPoints(VirtualMachine vm, ClassPrepareEvent event)
      throws AbsentInformationException {
    ClassType classType = (ClassType) event.referenceType();
    for (int lineNumber : breakPointLines) {
      Location location = classType.locationsOfLine(lineNumber).get(0);
      BreakpointRequest bpReq = vm.eventRequestManager().createBreakpointRequest(location);
      bpReq.enable();
    }
  }

  public void displayVariables(LocatableEvent event) throws IncompatibleThreadStateException,
      AbsentInformationException {
    StackFrame stackFrame = event.thread().frame(0);
    if (stackFrame.location().toString().contains(debugClass.getName())) {
      Map<LocalVariable, Value> visibleVariables = stackFrame
          .getValues(stackFrame.visibleVariables());
      System.out.println("Variables at " + stackFrame.location().toString() + " > ");
      for (Map.Entry<LocalVariable, Value> entry : visibleVariables.entrySet()) {
        System.out.println(entry.getKey().name() + " = " + entry.getValue());
      }
    }
  }

}