package org.ce.infrastructure.mcs;

import org.ce.application.port.MCSProgressPort;
import org.ce.application.port.MCSRunnerPort;
import org.ce.domain.model.result.EquilibriumState;
import org.ce.application.dto.MCSCalculationContext;
import org.ce.domain.mcs.MCResult;
import org.ce.domain.mcs.MCSRunner;

import java.util.function.BooleanSupplier;
import java.util.logging.Logger;
import org.ce.infrastructure.logging.LoggingConfig;

/**
 * Infrastructure adapter bridging the application MCS port to MCSRunner.
 */
public final class MCSRunnerAdapter implements MCSRunnerPort {

    private static final Logger LOG = LoggingConfig.getLogger(MCSRunnerAdapter.class);

    /**
     * Gas constant R = 8.314 J/(mol·K) for correct Boltzmann statistics
     * when ECI units are in J/mol.
     */
    private static final double GAS_CONSTANT = 8.314;

    @Override
    public EquilibriumState run(
            MCSCalculationContext context,
            MCSProgressPort progressPort,
            BooleanSupplier cancellationCheck) {

        LOG.fine("MCSRunnerAdapter.run — ENTER: system=" + context.getSystem().getId()
                + ", T=" + context.getTemperature() + " K, x=" + context.getComposition()
                + ", L=" + context.getSupercellSize()
                + ", nEquil=" + context.getEquilibrationSteps()
                + ", nAvg=" + context.getAveragingSteps());

        MCSRunner.Builder builder = MCSRunner.builder()
                .clusterData(context.getClusterData())
                .eci(context.getECI())
                .numComp(context.getNumComponents())
                .T(context.getTemperature())
                .composition(context.getComposition())
                .nEquil(context.getEquilibrationSteps())
                .nAvg(context.getAveragingSteps())
                .L(context.getSupercellSize())
                .seed(context.getSeed())
                .updateListener(update -> {
                    MCSProgressPort.MCSSnapshot snapshot = new MCSProgressPort.MCSSnapshot(
                            update.getStep(),
                            update.getE_total(),
                            update.getDeltaE(),
                            update.getSigmaDE(),
                            update.getMeanDE(),
                            switch (update.getPhase()) {
                                case EQUILIBRATION -> MCSProgressPort.SimulationPhase.EQUILIBRATION;
                                case AVERAGING -> MCSProgressPort.SimulationPhase.AVERAGING;
                            },
                            update.getAcceptanceRate(),
                            update.getElapsedMs());
                    progressPort.onMCSUpdate(snapshot);
                })
                .R(GAS_CONSTANT);

        if (cancellationCheck != null) {
            builder.cancellationCheck(cancellationCheck);
        }

        MCResult mcResult = builder.build().run();
        EquilibriumState result = EquilibriumState.fromMcs(
                mcResult.getTemperature(),
                mcResult.getComposition(),
                mcResult.getAvgCFs(),
                mcResult.getHmixPerSite(),
                mcResult.getHeatCapacityPerSite(),
                mcResult.getAcceptRate(),
                mcResult.getNEquilSweeps(),
                mcResult.getNAvgSweeps(),
                mcResult.getSupercellSize(),
                mcResult.getNSites(),
                mcResult.getEnergyPerSite());

        LOG.fine("MCSRunnerAdapter.run — EXIT: acceptRate="
                + String.format("%.3f", mcResult.getAcceptRate())
                + ", nSites=" + mcResult.getNSites()
                + ", Hmix/site=" + String.format("%.6f", mcResult.getHmixPerSite()));
        return result;
    }
}
