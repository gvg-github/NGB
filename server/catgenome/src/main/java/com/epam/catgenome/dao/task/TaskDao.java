/*
 * MIT License
 *
 * Copyright (c) 2021 EPAM Systems
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.epam.catgenome.dao.task;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.epam.catgenome.dao.DaoHelper;
import com.epam.catgenome.entity.task.Organism;
import com.epam.catgenome.entity.task.Task;
import com.epam.catgenome.entity.task.TaskParameter;
import com.epam.catgenome.entity.task.TaskStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Required;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcDaoSupport;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

public class TaskDao extends NamedParameterJdbcDaoSupport {
    private String taskSequenceName;
    private String insertTaskQuery;
    private String updateTaskQuery;
    private String deleteTaskQuery;
    private String loadTaskByIdQuery;
    private String loadAllTasksQuery;

    private String organismSequenceName;
    private String insertTaskOrganismsQuery;
    private String deleteTaskOrganismsQuery;
    private String loadTaskOrganismsQuery;

    private String taskParameterSequenceName;
    private String insertTaskParametersQuery;
    private String deleteTaskParametersQuery;
    private String loadTaskParametersQuery;

    @Autowired
    private DaoHelper daoHelper;

    /**
     * Saves or updates a {@code Task} instance in the database
     * @param task to save
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void saveTask(final Task task) {
        if(task.getId() == null) {
            task.setId(daoHelper.createId(taskSequenceName));
            getNamedParameterJdbcTemplate().update(insertTaskQuery, TaskParameters.getParameters(task));
        } else {
            getNamedParameterJdbcTemplate().update(updateTaskQuery, TaskParameters.getParameters(task));
        }
    }

    /**
     * Loads a {@code Task} instance from the database specified by it's ID
     * @param id of the task
     * @return a loaded {@code Task} instance or
     *          {@code null} if task with a given ID doesn't exist
     */
    @Transactional(propagation = Propagation.SUPPORTS)
    public Task loadTaskById(long id) {
        List<Task> tasks = getJdbcTemplate().query(loadTaskByIdQuery, TaskParameters.getRowMapper(), id);
        return tasks.isEmpty() ? null : tasks.get(0);
    }

    @Transactional(propagation = Propagation.SUPPORTS)
    public List<Task> loadAllTasks() {
        return getJdbcTemplate().query(loadAllTasksQuery, TaskParameters.getRowMapper());
    }

    /**
     * Deletes {@code Task}  from database.
     *
     * @param id of the task to remove
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void deleteTask(final Long id) {
        getJdbcTemplate().update(deleteTaskQuery, id);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void saveOrganism(final Organism organism) {
        organism.setId(daoHelper.createId(organismSequenceName));
        getNamedParameterJdbcTemplate().update(insertTaskOrganismsQuery, OrganismParameters.getParameters(organism));
    }

    /**
     * Loads {@code Organisms} from the database by Task Id
     * @param taskId id of the task
     * @return a loaded {@code List<Organism>}
     */
    @Transactional(propagation = Propagation.SUPPORTS)
    public List<Organism> loadOrganisms(long taskId) {
        return getJdbcTemplate().query(loadTaskOrganismsQuery, OrganismParameters.getRowMapper(), taskId);
    }

    /**
     * Deletes {@code Organisms}  from database.
     *
     * @param id of the task
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void deleteOrganisms(final Long id) {
        getJdbcTemplate().update(deleteTaskOrganismsQuery, id);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void saveTaskParameter(final TaskParameter taskParameter) {
        taskParameter.setParameterId(daoHelper.createId(taskParameterSequenceName));
        getNamedParameterJdbcTemplate().update(insertTaskParametersQuery, TaskParameterParameters.getParameters(taskParameter));
    }

    @Transactional(propagation = Propagation.SUPPORTS)
    public List<TaskParameter> loadTaskParameters(long id) {
        return getJdbcTemplate().query(loadTaskParametersQuery, TaskParameterParameters.getRowMapper(), id);
    }

    /**
     * Deletes {@code Task Parameters}  from database.
     *
     * @param id of the task
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void deleteParameters(final Long id) {
        getJdbcTemplate().update(deleteTaskParametersQuery, id);
    }

    enum TaskParameters {
        TASK_ID,
        TITLE,
        CREATED_DATE,
        STATUS,
        END_DATE,
        STATUS_REASON,
        QUERY,
        DATABASE,
        EXECUTABLE,
        ALGORITHM;

        static MapSqlParameterSource getParameters(Task task) {
            MapSqlParameterSource params = new MapSqlParameterSource();

            params.addValue(TASK_ID.name(), task.getId());
            params.addValue(TITLE.name(), task.getTitle());
            params.addValue(CREATED_DATE.name(), task.getCreatedDate());
            params.addValue(STATUS.name(), task.getStatus().getId());
            params.addValue(END_DATE.name(), task.getEndDate());
            params.addValue(STATUS_REASON.name(), task.getStatusReason());
            params.addValue(QUERY.name(), task.getQuery());
            params.addValue(DATABASE.name(), task.getDatabase());
            params.addValue(EXECUTABLE.name(), task.getExecutable());
            params.addValue(ALGORITHM.name(), task.getAlgorithm());

            return params;
        }

        static RowMapper<Task> getRowMapper() {
            return (rs, rowNum) -> {
                Task task = new Task();

                task.setId(rs.getLong(TASK_ID.name()));
                task.setTitle(rs.getString(TITLE.name()));
                task.setCreatedDate(rs.getTimestamp(CREATED_DATE.name()));
                task.setEndDate(rs.getTimestamp(END_DATE.name()));
                task.setStatusReason(rs.getString(STATUS_REASON.name()));
                task.setQuery(rs.getString(QUERY.name()));
                task.setDatabase(rs.getString(DATABASE.name()));
                task.setExecutable(rs.getString(EXECUTABLE.name()));
                task.setAlgorithm(rs.getString(ALGORITHM.name()));

                long longVal = rs.getLong(STATUS.name());
                if (!rs.wasNull()) {
                    task.setStatus(TaskStatus.getById(longVal));
                }

                return task;
            };
        }
    }

    enum OrganismParameters {
        ORGANISM_ID,
        TASK_ID,
        ORGANISM;

        static MapSqlParameterSource getParameters(Organism organism) {
            MapSqlParameterSource params = new MapSqlParameterSource();

            params.addValue(ORGANISM_ID.name(), organism.getId());
            params.addValue(TASK_ID.name(), organism.getTaskId());
            params.addValue(ORGANISM.name(), organism.getOrganism());

            return params;
        }

        static RowMapper<Organism> getRowMapper() {
            return (rs, rowNum) -> {
                Organism organism = new Organism();

                organism.setId(rs.getLong(ORGANISM_ID.name()));
                organism.setTaskId(rs.getLong(TASK_ID.name()));
                organism.setOrganism(rs.getString(ORGANISM.name()));

                return organism;
            };
        }
    }

    enum TaskParameterParameters {
        PARAMETER_ID,
        TASK_ID,
        PARAMETER,
        VALUE;

        static MapSqlParameterSource getParameters(TaskParameter taskParameter) {
            MapSqlParameterSource params = new MapSqlParameterSource();

            params.addValue(PARAMETER_ID.name(), taskParameter.getParameterId());
            params.addValue(TASK_ID.name(), taskParameter.getTaskId());
            params.addValue(PARAMETER.name(), taskParameter.getParameter());
            params.addValue(VALUE.name(), taskParameter.getValue());

            return params;
        }

        static RowMapper<TaskParameter> getRowMapper() {
            return (rs, rowNum) -> {
                TaskParameter taskParameter = new TaskParameter();

                taskParameter.setParameterId(rs.getLong(PARAMETER_ID.name()));
                taskParameter.setTaskId(rs.getLong(TASK_ID.name()));
                taskParameter.setParameter(rs.getString(PARAMETER.name()));
                taskParameter.setValue(rs.getString(VALUE.name()));

                return taskParameter;
            };
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void saveOrganisms(long taskId, List<String> organisms) {
        List<Long> newIds = daoHelper.createIds(organismSequenceName, organisms.size());

        ArrayList<MapSqlParameterSource> params = new ArrayList<>(organisms.size());
        for (int i = 0; i < organisms.size(); i++) {
            MapSqlParameterSource param = new MapSqlParameterSource();

            param.addValue(OrganismParameters.ORGANISM_ID.name(), newIds.get(i));
            param.addValue(OrganismParameters.TASK_ID.name(), taskId);
            param.addValue(OrganismParameters.ORGANISM.name(),
                    organisms.get(i));

            params.add(param);
        }

        getNamedParameterJdbcTemplate().batchUpdate(insertTaskOrganismsQuery,
                params.toArray(new MapSqlParameterSource[organisms.size()]));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void saveTaskParameters(long taskId, Map<String, String> parameters) {
        List<Long> newIds = daoHelper.createIds(taskParameterSequenceName, parameters.size());

        ArrayList<MapSqlParameterSource> params = new ArrayList<>(parameters.size());
        int i = 0;
        for (Map.Entry<String, String> entry : parameters.entrySet()) {
            MapSqlParameterSource param = new MapSqlParameterSource();

            param.addValue(TaskParameterParameters.PARAMETER_ID.name(), newIds.get(i));
            param.addValue(TaskParameterParameters.TASK_ID.name(), taskId);
            param.addValue(TaskParameterParameters.PARAMETER.name(), entry.getKey());
            param.addValue(TaskParameterParameters.VALUE.name(), entry.getValue());

            params.add(param);
            i++;
        }

        getNamedParameterJdbcTemplate().batchUpdate(insertTaskParametersQuery,
                params.toArray(new MapSqlParameterSource[parameters.size()]));
    }

    @Required
    public void setTaskSequenceName(String taskSequenceName) {
        this.taskSequenceName = taskSequenceName;
    }

    @Required
    public void setOrganismSequenceName(String organismSequenceName) {
        this.organismSequenceName = organismSequenceName;
    }

    @Required
    public void setParameterSequenceName(String taskParameterSequenceName) {
        this.taskParameterSequenceName = taskParameterSequenceName;
    }

    @Required
    public void setInsertTaskQuery(String insertTaskQuery) {
        this.insertTaskQuery = insertTaskQuery;
    }

    @Required
    public void setLoadTaskByIdQuery(String loadTaskByIdQuery) {
        this.loadTaskByIdQuery = loadTaskByIdQuery;
    }

    @Required
    public void setLoadAllTasksQuery(String loadAllTasksQuery) {
        this.loadAllTasksQuery = loadAllTasksQuery;
    }

    @Required
    public void setUpdateTaskQuery(String updateTaskQuery) {
        this.updateTaskQuery = updateTaskQuery;
    }

    @Required
    public void setDeleteTaskQuery(String deleteTaskQuery) {
        this.deleteTaskQuery = deleteTaskQuery;
    }

    @Required
    public void setInsertTaskOrganismsQuery(String insertTaskOrganismsQuery) {
        this.insertTaskOrganismsQuery = insertTaskOrganismsQuery;
    }

    @Required
    public void setDeleteTaskOrganismsQuery(String deleteTaskOrganismsQuery) {
        this.deleteTaskOrganismsQuery = deleteTaskOrganismsQuery;
    }

    @Required
    public void setLoadTaskOrganismsQuery(String loadTaskOrganismsQuery) {
        this.loadTaskOrganismsQuery = loadTaskOrganismsQuery;
    }

    @Required
    public void setInsertTaskParametersQuery(String insertTaskParametersQuery) {
        this.insertTaskParametersQuery = insertTaskParametersQuery;
    }

    @Required
    public void setDeleteTaskParametersQuery(String deleteTaskParametersQuery) {
        this.deleteTaskParametersQuery = deleteTaskParametersQuery;
    }

    @Required
    public void setLoadTaskParametersQuery(String loadTaskParametersQuery) {
        this.loadTaskParametersQuery = loadTaskParametersQuery;
    }
}
