package com.company.connector.pipeline;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Interface for pluggable data transformation pipelines.
 * Supports transformation of input data to output format with type safety.
 * 
 * @param <T> Input data type
 * @param <R> Output data type
 */
public interface DataTransformationPipeline<T, R> {
    
    /**
     * Transform a single input item to output format.
     * 
     * @param input The input data to transform
     * @return CompletableFuture containing the transformed output
     */
    CompletableFuture<R> transform(T input);
    
    /**
     * Transform a batch of input items to output format.
     * 
     * @param inputs List of input data to transform
     * @return CompletableFuture containing list of transformed outputs
     */
    CompletableFuture<List<R>> transformBatch(List<T> inputs);
    
    /**
     * Check if this pipeline supports the given input and output types.
     * 
     * @param inputType The input data type class
     * @param outputType The output data type class
     * @return true if this pipeline supports the transformation
     */
    boolean supports(Class<?> inputType, Class<?> outputType);
    
    /**
     * Get the unique identifier for this transformation pipeline.
     * 
     * @return Pipeline identifier
     */
    String getPipelineId();
    
    /**
     * Get the priority of this pipeline (higher values = higher priority).
     * Used when multiple pipelines support the same transformation.
     * 
     * @return Pipeline priority
     */
    default int getPriority() {
        return 0;
    }
}