package org.ce.infrastructure.mcs;

import org.ce.application.port.MCSProgressPort;
import org.ce.application.port.MCSRunnerPort;
import org.ce.domain.model.result.MCSResult;
import org.ce.infrastructure.context.MCSCalculationContext;
import org.ce.domain.mcs.MCResult;
import org.ce.domain.mcs.MCSRunner;

import java.util.function.BooleanSupplier;

/**
 * Infrastructure adapter bridging the application MCS port to MCSRunner.
 */
public final class MCSRunnerAdapter implements MCSRunnerPort {

    /**
     * Gas constant R = 8.314 J/(molÂ·K) for correct Boltzmann statistics
     * when ECI units are in J/mol.
     */
    private static final double GAS_CONSTANT = 8.314;

    @Override
    public MCSResult run(
            MCSCalculationContext context,
            MCSProgressPort progressPort,
            BooleanSupplier cancellationCheck) {

        MCSRunner.Builder builder = MCSRunner.builder()
                .clusterData(context.getClusterData())
                .eci(context.getECI())
                .numComp(context.getSystem().getNumComponents())
                .T(context.getTemperature())
                .compositionBinary(context.getComposition())
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
        return MCSResult.fromEngine(
                mcResult.getTemperature(),
                mcResult.getComposition(),
                mcResult.getAvgCFs(),
                mcResult.getEnergyPerSite(),
                mcResult.getHeatCapacityPerSite(),
                mcResult.getAcceptRate(),
                mcResult.getNEquilSweeps(),
                mcResult.getNAvgSweeps(),
                mcResult.getSupercellSize(),
                mcResult.getNSites());
    }
}

