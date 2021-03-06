/*
 * (C) Copyright IBM Corp. 2020, 2020
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.ibm.cohort.engine.measure;

import java.util.Map;

import org.apache.commons.lang3.tuple.Pair;
import org.hl7.fhir.r4.model.Library;
import org.hl7.fhir.r4.model.Measure;
import org.hl7.fhir.r4.model.MeasureReport;
import org.opencds.cqf.common.evaluation.EvaluationProviderFactory;
import org.opencds.cqf.common.providers.LibraryResolutionProvider;
import org.opencds.cqf.cql.engine.execution.LibraryLoader;
import org.opencds.cqf.r4.evaluation.MeasureEvaluation;
import org.opencds.cqf.r4.evaluation.MeasureEvaluationSeed;

import ca.uhn.fhir.rest.client.api.IGenericClient;

/**
 * Provide an interface for doing quality measure evaluation against a FHIR R4
 * server.
 * 
 * @todo - figure out how to support CDM (includes translation)
 * @todo - add support for pre-translated ELM instead of always doing the
 *       translation ourselves
 * @todo - add support for custom modelinfo during translation
 * @todo - reduce the number of calls to FHIR for library resolution
 * @todo - figure out how to capture/prevent the debugging from showing up in
 *       stdout
 * @todo - cqf-ruler populates supplementaldata in the MeasureReport. Do we want that?
 * @todo - add support for CDM population extensions "caregap"
 */
public class MeasureEvaluator {

	private IGenericClient dataClient;
	private IGenericClient terminologyClient;
	private IGenericClient measureClient;
	private MeasurementPeriodStrategy measurementPeriodStrategy;

	public MeasureEvaluator(IGenericClient dataClient, IGenericClient terminologyClient, IGenericClient measureClient) {
		this.dataClient = dataClient;
		this.terminologyClient = terminologyClient;
		this.measureClient = measureClient;
	}

	public void setMeasurementPeriodStrategy(MeasurementPeriodStrategy strategy) {
		this.measurementPeriodStrategy = strategy;
	}

	public MeasurementPeriodStrategy getMeasurementPeriodStrategy() {
		if (this.measurementPeriodStrategy == null) {
			this.measurementPeriodStrategy = new DefaultMeasurementPeriodStrategy();
		}
		return this.measurementPeriodStrategy;
	}

	public MeasureReport evaluatePatientMeasure(String measureId, String patientId, Map<String, Object> parameters) {
		Measure measure = measureClient.read().resource(Measure.class).withId(measureId).execute();
		return evaluatePatientMeasure(measure, patientId, parameters);
	}

	public MeasureReport evaluatePatientMeasure(Measure measure, String patientId, Map<String, Object> parameters) {
		Pair<String, String> period = getMeasurementPeriodStrategy().getMeasurementPeriod();
		return evaluatePatientMeasure(measure, patientId, period.getLeft(), period.getRight(), parameters);
	}

	public MeasureReport evaluatePatientMeasure(Measure measure, String patientId, String periodStart, String periodEnd,
			Map<String, Object> parameters) {
		LibraryResolutionProvider<Library> libraryResolutionProvider = new RestFhirLibraryResolutionProvider(
				measureClient);
		LibraryLoader libraryLoader = LibraryHelper.createLibraryLoader(libraryResolutionProvider);

		EvaluationProviderFactory factory = new ProviderFactory(dataClient, terminologyClient);
		MeasureEvaluationSeed seed = new MeasureEvaluationSeed(factory, libraryLoader, libraryResolutionProvider);

		// TODO - consider talking with OSS project about making source, user, and pass
		// a properties collection for more versatile configuration of the underlying
		// providers. For example, we need an additional custom HTTP header for
		// configuration of our FHIR server.
		seed.setup(measure, periodStart, periodEnd, /* productLine= */"ProductLine", /* source= */"", /* user= */"",
				/* pass= */"");

		// TODO - The OSS logic takes converts the period start and end into an
		// Interval and creates a parameter named "Measurement Period" that is populated
		// with that value. We need to sync with the authoring and clinical informatics
		// teams to confirm that every measure will have a measurement period and to
		// agree on what the name will be for that parameter. It is relevant during
		// MeasureReport generation as the value for the period attribute.

		// The OSS implementation doesn't support any additional parameter overrides, so
		// we need to add them ourselves.
		if (parameters != null) {
			for (Map.Entry<String, Object> entry : parameters.entrySet()) {
				seed.getContext().setParameter(null, entry.getKey(), entry.getValue());
			}
		}

		// The DaoRegistry parameter is only used for practitioner and subject-list type
		// measures, so we can safely null it out right now. It is based on HAPI JPA
		// ResourceProvider interface that we won't be able to use with IBM FHIR.
		MeasureEvaluation evaluation = new MeasureEvaluation(seed.getDataProvider(), /* daoRegistry= */null,
				seed.getMeasurementPeriod());
		return evaluation.evaluatePatientMeasure(measure, seed.getContext(), patientId);
	}
}
