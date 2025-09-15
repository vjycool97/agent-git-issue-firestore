package com.company.connector.service;

import com.company.connector.model.SyncResult;

import java.util.concurrent.CompletableFuture;

/**
 * Service interface for synchronizing GitHub issues with Firestore.
 * 
 * This interface defines the contract for the core synchronization operations
 * that orchestrate the complete process of fetching, transforming, and storing
 * GitHub issues in Firestore.
 */
public interface IssueService {
    
    /**
     * Synchronizes issues from a GitHub repository to Firestore.
     * 
     * This method orchestrates the complete sync process:
     * 1. Fetches recent issues from GitHub API
     * 2. Transforms GitHub issues to Firestore documents
     * 3. Checks for duplicates in Firestore
     * 4. Saves new/updated issues to Firestore
     * 
     * @param owner the repository owner (username or organization)
     * @param repo the repository name
     * @return a CompletableFuture containing the sync result
     * @throws IllegalArgumentException if owner or repo is null or blank
     */
    CompletableFuture<SyncResult> syncIssues(String owner, String repo);
    
    /**
     * Synchronizes issues from a GitHub repository to Firestore with a custom limit.
     * 
     * @param owner the repository owner (username or organization)
     * @param repo the repository name
     * @param limit the maximum number of issues to fetch and sync
     * @return a CompletableFuture containing the sync result
     * @throws IllegalArgumentException if owner or repo is null or blank
     * @throws IllegalArgumentException if limit is less than 1 or greater than 100
     */
    CompletableFuture<SyncResult> syncIssues(String owner, String repo, int limit);
}