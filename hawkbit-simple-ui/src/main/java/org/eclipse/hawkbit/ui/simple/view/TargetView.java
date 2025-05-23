/**
 * Copyright (c) 2023 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.hawkbit.ui.simple.view;

import java.util.Collections;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.time.ZoneOffset;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import java.util.stream.Stream;

import jakarta.annotation.security.RolesAllowed;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.Unit;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.ComponentEventListener;
import com.vaadin.flow.component.checkbox.CheckboxGroup;
import com.vaadin.flow.component.datetimepicker.DateTimePicker;
import com.vaadin.flow.component.dependency.Uses;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.theme.lumo.LumoUtility;
import org.eclipse.hawkbit.mgmt.json.model.MgmtPollStatus;
import org.eclipse.hawkbit.mgmt.json.model.distributionset.MgmtActionType;
import org.eclipse.hawkbit.mgmt.json.model.distributionset.MgmtDistributionSet;
import org.eclipse.hawkbit.mgmt.json.model.distributionset.MgmtTargetAssignmentRequestBody;
import org.eclipse.hawkbit.mgmt.json.model.tag.MgmtTag;
import org.eclipse.hawkbit.mgmt.json.model.target.MgmtTarget;
import org.eclipse.hawkbit.mgmt.json.model.target.MgmtTargetRequestBody;
import org.eclipse.hawkbit.mgmt.json.model.targetfilter.MgmtTargetFilterQuery;
import org.eclipse.hawkbit.mgmt.json.model.targetfilter.MgmtTargetFilterQueryRequestBody;
import org.eclipse.hawkbit.mgmt.json.model.targettype.MgmtTargetType;
import org.eclipse.hawkbit.ui.simple.HawkbitMgmtClient;
import org.eclipse.hawkbit.ui.simple.MainLayout;
import org.eclipse.hawkbit.ui.simple.view.util.Filter;
import org.eclipse.hawkbit.ui.simple.view.util.SelectionGrid;
import org.eclipse.hawkbit.ui.simple.view.util.TableView;
import org.eclipse.hawkbit.ui.simple.view.util.Utils;
import org.springframework.util.ObjectUtils;
import java.util.stream.Collectors;

@PageTitle("Targets")
@Route(value = "targets", layout = MainLayout.class)
@RolesAllowed({ "TARGET_READ" })
@Uses(Icon.class)
public class TargetView extends TableView<MgmtTarget, String> {

    public static final String STATUS = "Status";
    public static final String CONTROLLER_ID = "Controller Id";
    public static final String TAG = "Tag";

    public TargetView(final HawkbitMgmtClient hawkbitClient) {
        super(
                new RawFilter(hawkbitClient), new SimpleFilter(hawkbitClient),
                new SelectionGrid.EntityRepresentation<>(MgmtTarget.class, MgmtTarget::getControllerId) {

                    @Override
                    protected void addColumns(final Grid<MgmtTarget> grid) {
                        grid.addColumn(new ComponentRenderer<Component, MgmtTarget>(TargetStatusCell::new)).setHeader(STATUS).setAutoWidth(true)
                                .setFlexGrow(0);
                        grid.addColumn(MgmtTarget::getControllerId).setHeader(CONTROLLER_ID).setAutoWidth(true);
                        grid.addColumn(MgmtTarget::getName).setHeader(Constants.NAME).setAutoWidth(true);
                        grid.addColumn(MgmtTarget::getTargetTypeName).setHeader(Constants.TYPE).setAutoWidth(true);

                        grid.setItemDetailsRenderer(new ComponentRenderer<>(
                                () -> new TargetDetails(hawkbitClient), TargetDetails::setItem));
                    }
                },
                (query, filter) -> hawkbitClient.getTargetRestApi()
                        .getTargets(
                                query.getOffset(), query.getPageSize(), Constants.NAME_ASC,
                                filter)
                        .getBody()
                        .getContent()
                        .stream(),
                source -> new RegisterDialog(hawkbitClient).result(),
                selectionGrid -> {
                    selectionGrid.getSelectedItems().forEach(toDelete ->
                            hawkbitClient.getTargetRestApi().deleteTarget(toDelete.getControllerId()));
                    return CompletableFuture.completedFuture(null);
                });

        Function<SelectionGrid<MgmtTarget, String>, CompletionStage<Void>> assignHandler = source -> new AssignDialog(
                hawkbitClient, source.getSelectedItems()).result();

        final Button assignBtn = Utils.tooltip(new Button(VaadinIcon.LINK.create()), "Assign");
        assignBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        assignBtn.addClickListener(e -> assignHandler
                .apply(selectionGrid)
                .thenAccept(v -> selectionGrid.refreshGrid(false)));
        controlsLayout.addComponentAtIndex(0, assignBtn);
    }

    private static class SimpleFilter implements Filter.Rsql {

        private final HawkbitMgmtClient hawkbitClient;

        private final TextField controllerId;
        private final CheckboxGroup<MgmtTargetType> type;
        private final CheckboxGroup<MgmtTag> tag;

        private SimpleFilter(final HawkbitMgmtClient hawkbitClient) {
            this.hawkbitClient = hawkbitClient;

            controllerId = Utils.textField(CONTROLLER_ID);
            controllerId.setPlaceholder("<controller id filter>");
            type = new CheckboxGroup<>(Constants.TYPE);
            type.setItemLabelGenerator(MgmtTargetType::getName);
            tag = new CheckboxGroup<>(TAG);
            tag.setItemLabelGenerator(MgmtTag::getName);
        }

        @Override
        public List<Component> components() {
            final List<Component> components = new LinkedList<>();
            components.add(controllerId);
            type.setItems(hawkbitClient.getTargetTypeRestApi().getTargetTypes(0, 20, Constants.NAME_ASC, null).getBody().getContent());
            if (!type.getValue().isEmpty()) {
                components.add(type);
            }
            tag.setItems(hawkbitClient.getTargetTagRestApi().getTargetTags(0, 20, Constants.NAME_ASC, null).getBody().getContent());
            if (!tag.isEmpty()) {
                components.add(tag);
            }
            return components;
        }

        @Override
        public String filter() {
            return Filter.filter(
                    Map.of(
                            "controllerid", controllerId.getOptionalValue(),
                            "targettype.name", type.getSelectedItems().stream().map(MgmtTargetType::getName)
                                    .toList(),
                            "tag", tag.getSelectedItems()));
        }
    }

    @SuppressWarnings({ "java:S1171", "java:S3599" })
    private static class RawFilter implements Filter.Rsql {

        private final TextField textFilter = new TextField("Raw Filter", "<raw filter>");
        private final VerticalLayout layout = new VerticalLayout();
        private final Select<MgmtTargetFilterQuery> savedFilters = new Select<>();

        private RawFilter(final HawkbitMgmtClient hawkbitClient) {
            final Button createBtn = Utils.tooltip(new Button("Save", VaadinIcon.PLUS.create()), "Save");
            final Button updateBtn = Utils.tooltip(new Button(VaadinIcon.HARDDRIVE.create()), "Update");
            updateBtn.setEnabled(false);

            savedFilters.setLabel("Saved Filters");
            savedFilters.addValueChangeListener(e -> {
                if (e.getValue() != null) {
                    textFilter.setValue(e.getValue().getQuery());
                    updateBtn.setEnabled(true);
                    createBtn.setText("Save as");
                } else {
                    textFilter.clear();
                    updateBtn.setEnabled(false);
                    createBtn.setText("Save");
                }
            });
            savedFilters.setEmptySelectionAllowed(true);
            savedFilters.setItems(listFilters(hawkbitClient));
            savedFilters.setItemLabelGenerator(
                    query -> Optional.ofNullable(query).map(MgmtTargetFilterQuery::getName).orElse("<select saved filter>"));
            savedFilters.setWidthFull();

            textFilter.setWidthFull();
            createBtn.addClickListener(createBtnListener(hawkbitClient));
            updateBtn.addClickListener(updateBtnListener(hawkbitClient));

            layout.setSpacing(false);
            layout.setPadding(false);
            final HorizontalLayout textSaveLayout = new HorizontalLayout(textFilter, createBtn, updateBtn);
            textSaveLayout.setAlignItems(FlexComponent.Alignment.BASELINE);
            textSaveLayout.setWidthFull();
            layout.add(savedFilters, textSaveLayout);
            layout.addClassNames(LumoUtility.Gap.SMALL);
        }

        private static List<MgmtTargetFilterQuery> listFilters(HawkbitMgmtClient hawkbitClient) {
            return Optional.ofNullable(
                    hawkbitClient.getTargetFilterQueryRestApi()
                            .getFilters(0, 30, null, null, null)
                            .getBody().getContent()
            ).orElse(Collections.emptyList());
        }

        private ComponentEventListener<ClickEvent<Button>> createBtnListener(HawkbitMgmtClient hawkbitClient) {
            return e ->
                    new Utils.BaseDialog<Void>("Create New Filter") {

                        {
                            final Button finishBtn = Utils.tooltip(new Button("Save"), "Save (Enter)");
                            final TextField name = Utils.textField(
                                    Constants.NAME,
                                    e -> finishBtn.setEnabled(!e.getHasValue().isEmpty())
                            );
                            name.focus();
                            finishBtn.addClickShortcut(Key.ENTER);
                            finishBtn.setEnabled(false);
                            finishBtn.addClickListener(e -> {
                                final MgmtTargetFilterQueryRequestBody createRequest = new MgmtTargetFilterQueryRequestBody();
                                createRequest.setName(name.getValue());
                                createRequest.setQuery(textFilter.getValue());
                                hawkbitClient.getTargetFilterQueryRestApi().createFilter(createRequest);
                                savedFilters.setItems(
                                        listFilters(hawkbitClient));
                                close();
                            });
                            getFooter().add(finishBtn);
                            add(name);
                            open();
                        }
                    };
        }

        private ComponentEventListener<ClickEvent<Button>> updateBtnListener(HawkbitMgmtClient hawkbitClient) {
            return e -> {
                final MgmtTargetFilterQuery selected = savedFilters.getValue();
                if (selected == null) return;

                new Utils.BaseDialog<Void>("Update Filter") {

                    {
                        final Button finishBtn = Utils.tooltip(new Button("Update"), "Update (Enter)");
                        finishBtn.setEnabled(false);

                        final TextField name = Utils.textField(
                                Constants.NAME,
                                e -> finishBtn.setEnabled(!e.getHasValue().isEmpty())
                        );
                        name.focus();
                        name.setValue(selected.getName());

                        final TextArea filterValue = new TextArea("Filter Value");
                        filterValue.setReadOnly(true);
                        filterValue.setValue(textFilter.getValue());
                        filterValue.setWidthFull();

                        finishBtn.addClickShortcut(Key.ENTER);
                        finishBtn.addClickListener(e -> {
                            final MgmtTargetFilterQueryRequestBody updateRequest = new MgmtTargetFilterQueryRequestBody();
                            updateRequest.setName(name.getValue());
                            updateRequest.setQuery(textFilter.getValue());
                            hawkbitClient.getTargetFilterQueryRestApi().updateFilter(selected.getId(), updateRequest);
                            savedFilters.setItems(listFilters(hawkbitClient));
                            close();
                        });
                        getFooter().add(finishBtn);

                        add(name);
                        add(filterValue);
                        open();
                    }
                };
            };
        }

        @Override
        public List<Component> components() {
            return List.of(layout);
        }

        @Override
        public String filter() {
            return textFilter.getOptionalValue().orElse(null);
        }
    }

    private static class TargetDetails extends FormLayout {

        private final transient HawkbitMgmtClient hawkbitClient;
        private final TextArea description = new TextArea(Constants.DESCRIPTION);
        private final TextField createdBy = Utils.textField(Constants.CREATED_BY);
        private final TextField createdAt = Utils.textField(Constants.CREATED_AT);
        private final TextField lastModifiedBy = Utils.textField(Constants.LAST_MODIFIED_BY);
        private final TextField lastModifiedAt = Utils.textField(Constants.LAST_MODIFIED_AT);
        private final TextField securityToken = Utils.textField(Constants.SECURITY_TOKEN);
        private final TextArea targetAttributes = new TextArea(Constants.ATTRIBUTES);

        private TargetDetails(HawkbitMgmtClient hawkbitClient) {
            this.hawkbitClient = hawkbitClient;
            description.setMinLength(2);
            Stream.of(
                            description,
                            createdBy, createdAt,
                            lastModifiedBy, lastModifiedAt,
                            securityToken, targetAttributes)
                    .forEach(field -> {
                        field.setReadOnly(true);
                        add(field);
                    });

            setResponsiveSteps(new ResponsiveStep("0", 2));
            setColspan(description, 2);
        }

        private void setItem(final MgmtTarget target) {
            description.setValue(target.getDescription() == null ? "N/A" : target.getDescription());
            createdBy.setValue(target.getCreatedBy());
            createdAt.setValue(new Date(target.getCreatedAt()).toString());
            lastModifiedBy.setValue(target.getLastModifiedBy());
            lastModifiedAt.setValue(new Date(target.getLastModifiedAt()).toString());
            securityToken.setValue(target.getSecurityToken());
            var response = hawkbitClient.getTargetRestApi().getAttributes(target.getControllerId());
            if (response.getStatusCode().is2xxSuccessful()) {
                targetAttributes.setValue(Objects.requireNonNullElse(response.getBody(), Collections.emptyMap()).entrySet().stream().map(entry -> entry.getKey() + ": " +
                        entry.getValue()).collect(Collectors.joining("\n")));
            } else {
                targetAttributes.setValue("Error occurred fetching attributes from server: " + response.getStatusCode());
            }
        }
    }

    private static class RegisterDialog extends Utils.BaseDialog<Void> {

        private final Select<MgmtTargetType> type;
        private final TextField controllerId;
        private final TextField name;
        private final TextArea description;

        private RegisterDialog(final HawkbitMgmtClient hawkbitClient) {
            super("Register Target");

            final Button register = Utils.tooltip(new Button("Register"), "Register (Enter)");
            type = new Select<>(
                    "Type",
                    e -> {
                    },
                    hawkbitClient.getTargetTypeRestApi()
                            .getTargetTypes(0, 30, Constants.NAME_ASC, null)
                            .getBody()
                            .getContent()
                            .toArray(new MgmtTargetType[0]));
            type.setWidthFull();
            type.setEmptySelectionAllowed(true);
            type.setItemLabelGenerator(item -> item == null ? "" : item.getName());
            controllerId = Utils.textField(CONTROLLER_ID,
                    e -> register.setEnabled(!e.getHasValue().isEmpty()));
            controllerId.focus();
            name = Utils.textField(Constants.NAME);
            name.setWidthFull();
            description = new TextArea(Constants.DESCRIPTION);
            description.setMinLength(2);
            description.setWidthFull();

            addCreateClickListener(register, hawkbitClient);
            register.setEnabled(false);
            register.addClickShortcut(Key.ENTER);
            register.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
            final Button cancel = Utils.tooltip(new Button("Cancel"), "Cancel (Esc)");
            cancel.addClickListener(e -> close());
            cancel.addClickShortcut(Key.ESCAPE);
            getFooter().add(cancel);
            getFooter().add(register);

            final VerticalLayout layout = new VerticalLayout();
            layout.setSizeFull();
            layout.setPadding(true);
            layout.setSpacing(false);
            layout.add(type, controllerId, name, description);
            add(layout);
            open();
        }

        private void addCreateClickListener(final Button register, final HawkbitMgmtClient hawkbitClient) {
            register.addClickListener(e -> {
                final MgmtTargetRequestBody request = new MgmtTargetRequestBody()
                        .setControllerId(controllerId.getValue())
                        .setName(name.getValue())
                        .setDescription(description.getValue());
                if (!ObjectUtils.isEmpty(type.getValue())) {
                    request.setTargetType(type.getValue().getId());
                }
                hawkbitClient.getTargetRestApi().createTargets(
                                List.of(request))
                        .getBody()
                        .stream()
                        .findFirst()
                        .orElseThrow()
                        .getControllerId();
                close();
            });
        }
    }

    private static class AssignDialog extends Utils.BaseDialog<Void> {

        private final Select<MgmtDistributionSet> distributionSet;
        private final Select<MgmtActionType> actionType;
        private final DateTimePicker forceTime = new DateTimePicker("Force Time");
        private final Button assign = new Button("Assign");

        private AssignDialog(final HawkbitMgmtClient hawkbitClient, Set<MgmtTarget> selectedTargets) {
            super("Assign Distribution Set");

            distributionSet = new Select<>(
                    "Distribution Set",
                    this::readyToAssign,
                    Optional.ofNullable(
                                    hawkbitClient.getDistributionSetRestApi()
                                            .getDistributionSets(0, 30, Constants.NAME_ASC, null)
                                            .getBody())
                            .map(body -> body.getContent().toArray(new MgmtDistributionSet[0]))
                            .orElseGet(() -> new MgmtDistributionSet[0]));
            distributionSet.setRequiredIndicatorVisible(true);
            distributionSet.setItemLabelGenerator(distributionSetO ->
                    distributionSetO.getName() + ":" + distributionSetO.getVersion());
            distributionSet.setWidthFull();

            actionType = Utils.actionTypeControls(forceTime);

            assign.setEnabled(false);
            assign.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
            addAssignClickListener(hawkbitClient, selectedTargets);
            final Button cancel = Utils.tooltip(new Button("Cancel"), "Cancel (Esc)");
            cancel.addClickListener(e -> close());
            cancel.addClickShortcut(Key.ESCAPE);
            getFooter().add(cancel);
            getFooter().add(assign);

            final VerticalLayout layout = new VerticalLayout();
            layout.setSizeFull();
            layout.setSpacing(false);
            layout.add(distributionSet, actionType);
            add(layout);
            open();
        }

        private void readyToAssign(final Object v) {
            final boolean assignEnabled = !distributionSet.isEmpty();
            if (assign.isEnabled() != assignEnabled) {
                assign.setEnabled(assignEnabled);
            }
        }

        private void addAssignClickListener(final HawkbitMgmtClient hawkbitClient, final Set<MgmtTarget> selectedTargets) {
            assign.addClickListener(e -> {
                close();
                List<MgmtTargetAssignmentRequestBody> requests = new LinkedList<MgmtTargetAssignmentRequestBody>();

                for (final MgmtTarget target : selectedTargets) {

                    MgmtTargetAssignmentRequestBody request = new MgmtTargetAssignmentRequestBody(target.getControllerId());

                    request.setType(actionType.getValue());
                    if (actionType.getValue() == MgmtActionType.TIMEFORCED) {
                        request.setForcetime(
                                forceTime.isEmpty() ?
                                        System.currentTimeMillis() :
                                        forceTime.getValue().toEpochSecond(ZoneOffset.UTC) * 1000);
                    }

                    requests.add(request);
                }

                hawkbitClient.getDistributionSetRestApi().createAssignedTarget(distributionSet.getValue().getId(), requests, null).getBody();
            });
        }
    }

    private static class TargetStatusCell extends HorizontalLayout {
        private TargetStatusCell(MgmtTarget target) {
            MgmtPollStatus pollStatus = target.getPollStatus();
            String targetUpdateStatus = Optional.ofNullable(target.getUpdateStatus()).orElse("unknown");
            add(pollStatusIconMapper(pollStatus), targetUpdateStatusMapper(targetUpdateStatus));
            setWidth(50, Unit.PIXELS);
        }

        private Icon targetUpdateStatusMapper(String targetUpdateStatus) {
            VaadinIcon icon = switch (targetUpdateStatus) {
                case "error" -> VaadinIcon.EXCLAMATION_CIRCLE;
                case "in_sync" -> VaadinIcon.CHECK_CIRCLE;
                case "pending" -> VaadinIcon.ADJUST;
                case "registered" -> VaadinIcon.DOT_CIRCLE;
                default -> VaadinIcon.QUESTION_CIRCLE;
            };

            String color = switch (targetUpdateStatus) {
                case "error" -> "red";
                case "in_sync" -> "green";
                case "pending" -> "orange";
                case "registered" -> "lightblue";
                default -> "blue";
            };

            Icon statusIcon = Utils.tooltip(icon.create(), targetUpdateStatus);
            statusIcon.setColor(color);
            statusIcon.addClassNames(LumoUtility.IconSize.SMALL);
            return statusIcon;
        }

        private Icon pollStatusIconMapper(MgmtPollStatus pollStatus) {
            Icon pollIcon;
            if (pollStatus == null) {
                pollIcon = Utils.tooltip(VaadinIcon.QUESTION_CIRCLE.create(), "No Poll Status");
            } else if (pollStatus.isOverdue()) {
                pollIcon = Utils.tooltip(VaadinIcon.EXCLAMATION_CIRCLE.create(), "Overdue");
            } else {
                pollIcon = Utils.tooltip(VaadinIcon.CLOCK.create(), "In Time");
            }
            pollIcon.addClassNames(LumoUtility.IconSize.SMALL);
            return pollIcon;
        }
    }
}
