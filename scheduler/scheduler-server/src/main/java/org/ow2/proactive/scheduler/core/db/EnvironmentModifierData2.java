package org.ow2.proactive.scheduler.core.db;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.JoinColumns;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

import org.ow2.proactive.scheduler.common.task.PropertyModifier;


@Entity
@Table(name = "ENVIRONMENT_MODIFIER_DATA2")
public class EnvironmentModifierData2 {

    private long id;

    private String name;

    private String value;

    private boolean append;

    private char appendChar;

    private TaskData taskData;

    static EnvironmentModifierData2 create(PropertyModifier propertyModifier, TaskData taskData) {
        EnvironmentModifierData2 data = new EnvironmentModifierData2();
        data.setName(propertyModifier.getName());
        data.setValue(propertyModifier.getValue());
        data.setAppend(propertyModifier.isAppend());
        data.setAppendChar(propertyModifier.getAppendChar());
        data.setTaskData(taskData);
        return data;
    }

    @Id
    @GeneratedValue
    @Column(name = "ID")
    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumns(value = { @JoinColumn(name = "JOB_ID", referencedColumnName = "TASK_ID_JOB"),
      @JoinColumn(name = "TASK_ID", referencedColumnName = "TASK_ID_TASK") })
    public TaskData getTaskData() {
        return taskData;
    }

    public void setTaskData(TaskData taskData) {
        this.taskData = taskData;
    }

    @Column(name = "NAME", nullable = false)
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Column(name = "VALUE", nullable = false)
    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    @Column(name = "APPEND")
    public boolean isAppend() {
        return append;
    }

    public void setAppend(boolean append) {
        this.append = append;
    }

    @Column(name = "APPEND_CHAR")
    public char getAppendChar() {
        return appendChar;
    }

    public void setAppendChar(char appendChar) {
        this.appendChar = appendChar;
    }

}
