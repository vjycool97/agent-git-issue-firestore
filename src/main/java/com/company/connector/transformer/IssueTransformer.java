package com.company.connector.transformer;

import com.company.connector.model.FirestoreIssueDocument;
import com.company.connector.model.GitHubIssue;

import java.util.List;

/**
 * Interface for transforming GitHub issues to Firestore documents.
 * 
 * This interface defines the contract for data transformation operations
 * between GitHub API responses and Firestore document format.
 */
public interface IssueTransformer {
    
    /**
     * Transforms a single GitHub issue to a Firestore document.
     * 
     * @param issue the GitHub issue to transform
     * @return the transformed Firestore document
     * @throws TransformationException if the transformation fails due to invalid data
     */
    FirestoreIssueDocument transform(GitHubIssue issue);
    
    /**
     * Transforms a batch of GitHub issues to Firestore documents.
     * 
     * @param issues the list of GitHub issues to transform
     * @return the list of transformed Firestore documents
     * @throws TransformationException if any transformation fails
     */
    List<FirestoreIssueDocument> transformBatch(List<GitHubIssue> issues);
}