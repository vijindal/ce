package org.ce.presentation.gui.component;

import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.ce.application.dto.DataReadinessStatus;
import org.ce.domain.system.SystemIdentity;
import org.ce.infrastructure.registry.KeyUtils;
import org.ce.infrastructure.service.DataManagementAdapter;

/**
 * Visual indicator component showing data readiness status.
 *
 * <p>Displays colored badges for cluster and CEC availability, enabling users
 * to quickly see if a system is ready for MCS/CVM calculations.</p>
 *
 * <p><strong>Visual Design:</strong>
 * <ul>
 *   <li>Green badge with checkmark: Data available ✓</li>
 *   <li>Gray badge with X: Data missing ✗</li>
 *   <li>Compact layout: "Clusters: ✓  CEC: ✗  CFs: ✓"</li>
 * </ul>
 *
 * <p><strong>Usage:</strong>
 * <pre>{@code
 * DataReadinessIndicator indicator = new DataReadinessIndicator(dataPort);
 * // When system selection changes:
 * indicator.updateStatus(system);
 * }</pre>
 *
 * @since Phase 4
 */
public class DataReadinessIndicator extends VBox {

    private final DataManagementAdapter dataPort;

    // Status labels
    private final Label clusterBadge;
    private final Label cecBadge;
    private final Label cfsBadge;
    private final Label statusMessage;

    /**
     * Creates a new data readiness indicator.
     *
     * @param dataPort the data management port for readiness checks
     */
    public DataReadinessIndicator(DataManagementAdapter dataPort) {
        this.dataPort = dataPort;

        setSpacing(4);
        setPadding(new Insets(0));

        // Create status badges
        clusterBadge = createBadge("Clusters");
        cecBadge = createBadge("CEC");
        cfsBadge = createBadge("CFs");

        // Arrange badges horizontally
        HBox badgeRow = new HBox(8, clusterBadge, cecBadge, cfsBadge);
        badgeRow.setPadding(new Insets(0));

        // Status message below badges
        statusMessage = new Label();
        statusMessage.setStyle("-fx-font-size: 9; -fx-text-fill: #666;");
        statusMessage.setWrapText(true);

        // Add to this panel
        getChildren().addAll(badgeRow, statusMessage);

        // Initialize to "no system" state
        setUnavailable();
    }

    /**
     * Updates the indicator status for a given system.
     *
     * @param system the system to check (null clears the indicator)
     */
    public void updateStatus(SystemIdentity system) {
        if (system == null) {
            setUnavailable();
            return;
        }

        DataReadinessStatus status = new DataReadinessStatus(
            dataPort.isClusterDataAvailable(KeyUtils.clusterKey(system)),
            dataPort.isCecAvailable(KeyUtils.cecKey(system)),
            dataPort.isCfsComputed(system.getId()),
            ""
        );

        // Update badges
        updateBadge(clusterBadge, status.clusterAvailable());
        updateBadge(cecBadge, status.cecAvailable());
        updateBadge(cfsBadge, status.cfsAvailable());

        // Update message
        if (status.isReadyForCVM()) {
            statusMessage.setText("✓ Ready for MCS & CVM calculations");
            statusMessage.setStyle("-fx-font-size: 9; -fx-text-fill: #006600; -fx-font-weight: bold;");
        } else if (status.isReadyForMCS()) {
            statusMessage.setText("✓ Ready for MCS (missing CF data for CVM)");
            statusMessage.setStyle("-fx-font-size: 9; -fx-text-fill: #006600;");
        } else {
            statusMessage.setText("✗ Missing required data — run identification pipeline");
            statusMessage.setStyle("-fx-font-size: 9; -fx-text-fill: #cc0000;");
        }
    }

    /**
     * Clears the indicator to "no system" state.
     */
    public void setUnavailable() {
        updateBadge(clusterBadge, false);
        updateBadge(cecBadge, false);
        updateBadge(cfsBadge, false);
        statusMessage.setText("No system selected");
        statusMessage.setStyle("-fx-font-size: 9; -fx-text-fill: #999; -fx-font-style: italic;");
    }

    /**
     * Creates a single status badge label.
     */
    private Label createBadge(String label) {
        Label badge = new Label();
        badge.setPadding(new Insets(2, 6, 2, 6));
        badge.setStyle(
            "-fx-border-radius: 3; " +
            "-fx-font-size: 9; " +
            "-fx-text-fill: #ffffff;"
        );
        return badge;
    }

    /**
     * Updates a badge's appearance based on availability.
     */
    private void updateBadge(Label badge, boolean available) {
        if (available) {
            badge.setText("✓ " + getText(badge));
            badge.setStyle(
                "-fx-background-color: #00aa00; " +
                "-fx-border-radius: 3; " +
                "-fx-padding: 2 6; " +
                "-fx-font-size: 9; " +
                "-fx-text-fill: #ffffff; " +
                "-fx-font-weight: bold;"
            );
        } else {
            badge.setText("✗ " + getText(badge));
            badge.setStyle(
                "-fx-background-color: #cccccc; " +
                "-fx-border-radius: 3; " +
                "-fx-padding: 2 6; " +
                "-fx-font-size: 9; " +
                "-fx-text-fill: #666666; " +
                "-fx-font-weight: bold;"
            );
        }
    }

    /**
     * Extracts the base label text from a badge (removes the checkmark/X prefix).
     */
    private String getText(Label badge) {
        String text = badge.getText();
        if (text != null && text.length() > 2) {
            return text.substring(2).trim();
        }
        return "Unknown";
    }
}
