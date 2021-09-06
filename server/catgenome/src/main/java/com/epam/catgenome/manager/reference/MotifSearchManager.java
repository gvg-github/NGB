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

package com.epam.catgenome.manager.reference;

import com.epam.catgenome.constant.MessagesConstants;
import com.epam.catgenome.entity.reference.Chromosome;
import com.epam.catgenome.entity.reference.StrandedSequence;
import com.epam.catgenome.entity.reference.motif.Motif;
import com.epam.catgenome.entity.reference.motif.MotifSearchRequest;
import com.epam.catgenome.entity.reference.motif.MotifSearchResult;
import com.epam.catgenome.entity.reference.motif.MotifSearchType;
import com.epam.catgenome.entity.track.Track;
import com.epam.catgenome.manager.gene.parser.StrandSerializable;
import com.epam.catgenome.util.MotifSearcher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.epam.catgenome.component.MessageHelper.getMessage;

@Service
@Slf4j
public class MotifSearchManager {

    @Value("${motif.search.buffer.size:16000000}")
    private int bufferSize;

    @Value("${motif.search.sliding.window:1000}")
    private int defaultOverlap;

    @Value("${motif.search.page.size:100}")
    private int defaultPageSize;

    @Value("${motif.search.include.sequence:false}")
    private boolean defaultIncludeSequence;

    @Autowired
    private ReferenceGenomeManager referenceGenomeManager;

    @Autowired
    private ReferenceManager referenceManager;


    public Track<StrandedSequence> fillTrackWithMotifSearch(final Track<StrandedSequence> track,
                                                            final String motif,
                                                            final StrandSerializable strand) {
        final List<StrandedSequence> result = search(
                MotifSearchRequest.builder()
                        .referenceId(track.getId())
                        .startPosition(track.getStartIndex())
                        .endPosition(track.getEndIndex())
                        .chromosomeId(track.getChromosome().getId())
                        .motif(motif)
                        .searchType(MotifSearchType.REGION)
                        .pageSize(Integer.MAX_VALUE)
                        .strand(strand)
                        .build()).getResult().stream()
                .map(m -> new StrandedSequence(m.getStart(), m.getEnd(), m.getSequence(), m.getStrand()))
                .collect(Collectors.toList());
        track.setBlocks(result);
        return track;
    }

    public MotifSearchResult search(final MotifSearchRequest request) {
        verifyMotifSearchRequest(request);
        switch (request.getSearchType()) {
            case WHOLE_GENOME:
                return searchWholeGenomeMotifs(request);
            case CHROMOSOME:
                return searchChromosomeMotifs(request);
            case REGION:
                return searchRegionMotifs(request);
            default:
                throw new IllegalStateException("Unexpected search type: " + request.getSearchType());
        }
    }

    private void verifyMotifSearchRequest(final MotifSearchRequest request) {
        Assert.notNull(request.getSearchType(), getMessage("Search type is empty!"));
        Assert.notNull(request.getMotif(), getMessage("Motif is empty!"));
        Assert.notNull(request.getReferenceId(), getMessage("Genome id is empty!"));
        final Integer start = request.getStartPosition();
        final Integer end = request.getEndPosition();
        final MotifSearchType searchType = request.getSearchType();
        if (searchType.equals(MotifSearchType.WHOLE_GENOME)) {
            return;
        }
        Assert.notNull(request.getChromosomeId(), getMessage("Chromosome not provided!"));
        if (end != null && start != null) {
            Assert.isTrue(end - start > 0,
                    getMessage("Provided end and start are not valid: " + end + " < " + start));
        }
        if (searchType.equals(MotifSearchType.CHROMOSOME)) {
            return;
        }
        Assert.notNull(start, getMessage("Start position is empty!"));
        Assert.notNull(end, getMessage("End position is empty!"));
    }

    private MotifSearchResult searchRegionMotifs(final MotifSearchRequest request) {
        final Chromosome chromosome = loadChrById(request.getReferenceId(), request.getChromosomeId());
        Assert.isTrue(request.getEndPosition() == null || request.getEndPosition() <= chromosome.getSize(),
                getMessage(MessagesConstants.ERROR_POSITION_OUT_OF_RANGE, request.getEndPosition()));
        final boolean includeSequence = request.getIncludeSequence() == null
                        ? defaultIncludeSequence
                        : request.getIncludeSequence();
        final List<Motif> searchResult =
                MotifSearcher.search(getSequence(request, chromosome), request.getMotif(), request.getStrand(),
                        chromosome.getName(), request.getStartPosition(), includeSequence);
        final int lastStart = searchResult.isEmpty()
                ? request.getStartPosition()
                : searchResult.get(searchResult.size() - 1).getStart();
        return MotifSearchResult.builder()
                .result(searchResult)
                .chromosomeId(request.getChromosomeId())
                .pageSize(searchResult.size())
                .position(lastStart < chromosome.getSize() ? lastStart + 1 : null)
                .build();
    }

    private byte[] getSequence(final MotifSearchRequest request, final Chromosome chromosome) {
        final byte[] sequence;
        try {
            sequence= referenceManager.getSequenceByteArray(request.getStartPosition(),
                            request.getEndPosition(), request.getReferenceId(), chromosome.getName());
        } catch (IOException e) {
            throw new IllegalStateException(getMessage(MessagesConstants.ERROR_REFERENCE_SEQUENCE_READING));
        }
        return sequence;
    }

    private MotifSearchResult searchChromosomeMotifs(final MotifSearchRequest request) {
        final Chromosome chromosome = loadChrById(request.getReferenceId(), request.getChromosomeId());
        Assert.isTrue(request.getEndPosition() == null || request.getEndPosition() <= chromosome.getSize(),
                getMessage(MessagesConstants.ERROR_POSITION_OUT_OF_RANGE, request.getEndPosition()));
        final int pageSize = request.getPageSize() == null || request.getPageSize() <= 0
                ? defaultPageSize
                : request.getPageSize();
        final int start = request.getStartPosition() == null ? 0 : request.getStartPosition();
        final int end = request.getEndPosition() == null ? chromosome.getSize() : request.getEndPosition();

        final int bufferSize = Math.min(this.bufferSize, end - start);
        int overlap = validateAndAdjustOverlap(request, bufferSize);

        final Set<Motif> result = new LinkedHashSet<>();
        int currentStart = start;
        int currentEnd = bufferSize + start;

        while (result.size() < pageSize && currentStart < end) {

            result.addAll(searchRegionMotifs(MotifSearchRequest.builder()
                    .motif(request.getMotif())
                    .referenceId(request.getReferenceId())
                    .chromosomeId(request.getChromosomeId())
                    .startPosition(currentStart)
                    .endPosition(currentEnd)
                    .strand(request.getStrand())
                    .build()
                    ).getResult());

            currentStart = currentStart + bufferSize - overlap;
            currentEnd = Math.min(currentEnd + bufferSize - overlap, end);
        }
        final List<Motif> pageSizedResult = result.stream()
                .limit(Math.min(result.size(), pageSize))
                .collect(Collectors.toList());
        final Integer lastStartMotifPosition = pageSizedResult.isEmpty() ||
                (end == chromosome.getSize() && pageSizedResult.size() < pageSize)
                ? null
                : pageSizedResult.get(pageSizedResult.size() - 1).getStart();
        return MotifSearchResult.builder()
                .result(pageSizedResult)
                .chromosomeId(request.getChromosomeId())
                .pageSize(pageSizedResult.size())
                .position(lastStartMotifPosition == null || lastStartMotifPosition.equals(chromosome.getSize())
                        ? null
                        : lastStartMotifPosition + 1)
                .build();
    }

    private int validateAndAdjustOverlap(MotifSearchRequest request, int bufferSize) {
        int overlap = request.getSlidingWindow() == null
                || request.getSlidingWindow() <= 0
                || request.getSlidingWindow() >= bufferSize
                ? defaultOverlap
                : request.getSlidingWindow();
        if (bufferSize < this.bufferSize) {
            overlap = 0;
        }
        return overlap;
    }

    private MotifSearchResult searchWholeGenomeMotifs(final MotifSearchRequest request) {
        final int pageSize = request.getPageSize() == null ? defaultPageSize : request.getPageSize();
        int start = request.getStartPosition() == null ? 0 : request.getStartPosition();
        Chromosome chromosome = loadChrById(request.getReferenceId(), request.getChromosomeId());
        long chrId = chromosome.getId();
        final int end = request.getEndPosition() == null ? chromosome.getSize() : request.getEndPosition();
        if (end < chromosome.getSize()) {
            return searchRegionMotifs(
                    request.toBuilder()
                            .pageSize(pageSize)
                            .searchType(MotifSearchType.REGION)
                            .chromosomeId(chrId)
                            .startPosition(start)
                            .endPosition(end)
                            .build());
        }
        final List<Chromosome> chromosomes = getChromosomesOfGenome(request.getReferenceId());
        final List<Motif> motifs = new ArrayList<>();
        while (chromosome != null && pageSize - motifs.size() > 0) {
            chrId = chromosome.getId();
            motifs.addAll(searchChromosomeMotifs(
                    request.toBuilder()
                            .pageSize(pageSize - motifs.size())
                            .searchType(MotifSearchType.CHROMOSOME)
                            .chromosomeId(chrId)
                            .startPosition(start)
                            .endPosition(chromosome.getSize())
                            .build())
                    .getResult()
                    .stream()
                    .limit(pageSize - motifs.size())
                    .collect(Collectors.toList()));
            start = 0;
            chromosome = getNextChromosome(chromosomes, chromosome);
        }
        return MotifSearchResult.builder()
                .result(motifs)
                .pageSize(pageSize)
                .chromosomeId(chrId)
                .position(chromosome == null && pageSize - motifs.size() > 0 ? null
                        : motifs.get(motifs.size() - 1).getStart())
                .build();
    }

    private Chromosome getNextChromosome(final List<Chromosome> chromosomes, final Chromosome chromosome) {
        for (int i = 0; i < chromosomes.size() - 1; i++) {
            if (chromosomes.get(i).getName().equals(chromosome.getName())) {
                return chromosomes.get(i + 1);
            }
        }
        return null;
    }

    private Chromosome loadChrById(final Long referenceId, final Long chromosomeId) {
        if (chromosomeId == null) {
            return getFirstChromosomeFromGenome(referenceId);
        } else {
            return referenceGenomeManager.loadChromosome(chromosomeId);
        }
    }

    private Chromosome getFirstChromosomeFromGenome(final Long referenceId) {
        return referenceGenomeManager.loadChromosomes(referenceId).get(0);
    }

    private List<Chromosome> getChromosomesOfGenome(final Long referenceId) {
        return referenceGenomeManager.loadChromosomes(referenceId);
    }
}