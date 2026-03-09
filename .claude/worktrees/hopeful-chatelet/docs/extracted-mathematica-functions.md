# Extracted Mathematica Functions from clusGen.23.nb

All functions extracted from `c:\Users\admin\Dropbox\Proj\2-gibbs-cvm\proj-multiComp-cecvm\clusGen.23.nb` with box formatting stripped to readable Mathematica code.

---

## 1. `genBasisSymbolList` (line ~2127)

```mathematica
(* Returns list of basis symbol operators {siteOpSymbol[1], ..., siteOpSymbol[numComp-1]} *)
genBasisSymbolList[numComp_, siteOpSymbol_] := Module[{i},
    Return[Table[siteOpSymbol[i], {i, 1, numComp - 1}]];
]
```

---

## 2. `genConfig` (line ~3406)

```mathematica
(* New Version, Returns all possible configurations of elements on the cluster sites *)
genConfig[clusCoord_, numElements_] := Module[
    {configList = {}, tempConfigList, numClusSite, flattenClusCoord, tempConfig, numBasis, transBasis, i},
    flattenClusCoord = Flatten[clusCoord, 1];
    (* numBasis = Length[basis]; *)
    transBasis = Table[i, {i, 1, numElements}];
    (* Print["transBasis:", transBasis]; *)
    numClusSite = Length[flattenClusCoord];
    tempConfigList = Tuples[transBasis, numClusSite];
    configList = tempConfigList;
    Return[configList];
]
```

---

## 3. `genRMat` (line ~13700)

```mathematica
(* Generates R Matrix: Old Version, Based on Inden(1992) formulation *)
(* Generates R Matrix which connects Site occupation operator vector pVec(pA, pB, pC...) 
   with site operator vector sVec (1, s1, s2,..), pVec = MMat.sVec *)
genRMat[numElements_] := Module[
    {numBasis, i, j, matM = {}, rowM, RMat, basis = {}},
    If[Mod[numElements, 2] == 0,
        (* Even Case *)
        For[i = 1, i <= (numElements/2), i++,
            basis = Append[basis, -(numElements/2) + (i - 1)];
        ];
        For[i = 1, i <= (numElements/2), i++,
            basis = Append[basis, 1 + (i - 1)];
        ];,
        (* Odd Case *)
        For[i = 1, i <= numElements, i++,
            basis = Append[basis, -((numElements - 1)/2) + (i - 1)];
        ];
    ];
    (* Print["basis:", basis]; *)
    For[i = 1, i <= numElements, i++,
        rowM = {};
        For[j = 1, j <= numElements, j++,
            rowM = Append[rowM, If[(i - 1) == 0, 1, basis[[j]]^(i - 1)]];
        ];
        matM = Append[matM, rowM];
    ];
    (* Print["matM:", matM]; *)
    RMat = Inverse[matM];
    Return[RMat];
]
```

---

## 4. `genSiteList` (line ~13880, second version)

```mathematica
(* Second version: creates list of (unique) site-coordinates present in maxClusCoord,
   this list will be used throughout the code for giving sites an index, 
   more useful for more than one maximal cluster, 
   redundant for the case of one maximal cluster *)
genSiteList[maxClusCoord_] := Module[
    {numMaximalCluster, siteList, index, i, j, k},
    numMaximalCluster = Length[maxClusCoord];
    siteList = {};
    index = 1;
    For[i = 1, i <= numMaximalCluster, i++, (* Loop over maximal clusters *)
        For[j = 1, j <= Length[maxClusCoord[[i]]], j++, (* Loop over sublattices *)
            For[k = 1, k <= Length[maxClusCoord[[i]][[j]]], k++, (* loop over sites *)
                If[MemberQ[siteList, maxClusCoord[[i]][[j]][[k]]],, (* not a member *)
                    siteList = Append[siteList, maxClusCoord[[i]][[j]][[k]]];
                    index = index + 1;
                ];
            ];
        ];
    ];
    Return[siteList];
]
```

---

## 5. `genSubstituteRules` (line ~13970, new version 180123)

```mathematica
(* New Version (180123): Returns substitutions rules for correlation functions *)
genSubstituteRules[cfSiteOpList_, cfSymbol_] := Module[
    {i, j, k, l, m, n, siteOp, rules, tempClusCoord},
    (* substitution rules *)
    rules = {};
    For[i = 1, i <= Length[cfSiteOpList], i++, (* loop over clusters *)
        rules = Append[rules, {}];
        For[j = 1, j <= Length[cfSiteOpList[[i]]], j++, (* loop over hsp cf group *)
            rules[[i]] = Append[rules[[i]], {}];
            For[k = 1, k <= Length[cfSiteOpList[[i]][[j]]], k++, (* loop over cluster groups *)
                rules[[i]][[j]] = Append[rules[[i]][[j]], {}];
                For[l = 1, l <= Length[cfSiteOpList[[i]][[j]][[k]]], l++, (* loop over individual cluster *)
                    tempClusCoord = cfSiteOpList[[i]][[j]][[k]][[l]];
                    siteOp = 1;
                    For[n = 1, n <= Length[tempClusCoord], n++, (* Loop over sites *)
                        siteOp = siteOp * tempClusCoord[[n]];
                    ];
                    rules[[i]][[j]][[k]] = Append[rules[[i]][[j]][[k]], 
                        siteOp -> cfSymbol[i][j][k]];
                ];
            ];
        ];
    ];
    Return[DeleteDuplicates[Flatten[rules, 3]]];
]
```

---

## 6. `genCfSiteOpList` (line ~14169)

```mathematica
(* 180123: Makes a list of site operators for each cf group *)
genCfSiteOpList[groupClusCoordList_, siteList_] := Module[
    {i, j, k, l, m, n, siteOp, siteOpList, rules, tc, tempClusCoord},
    (* substitution rules *)
    rules = {};
    For[i = 1, i < Length[groupClusCoordList], i++, (* loop over clusters *)
        rules = Append[rules, {}];
        For[j = 1, j <= Length[groupClusCoordList[[i]]], j++, (* loop over hsp cf group *)
            rules[[i]] = Append[rules[[i]], {}];
            For[k = 1, k <= Length[groupClusCoordList[[i]][[j]]], k++, (* loop over cluster groups *)
                rules[[i]][[j]] = Append[rules[[i]][[j]], {}];
                For[l = 1, l <= Length[groupClusCoordList[[i]][[j]][[k]]], l++, (* loop over individual cluster *)
                    tempClusCoord = groupClusCoordList[[i]][[j]][[k]][[l]];
                    siteOp = {};
                    For[n = 1, n <= Length[tempClusCoord], n++, (* Loop over sublattice *)
                        For[m = 1, m <= Length[tempClusCoord[[n]]], m++, (* loop over sites *)
                            siteOp = Append[siteOp,
                                tempClusCoord[[n]][[m]][[2]][
                                    Position[siteList, tempClusCoord[[n]][[m]][[1]]][[1]][[1]]
                                ]
                            ];
                        ];
                    ];
                    rules[[i]][[j]][[k]] = Append[rules[[i]][[j]][[k]], siteOp];
                ];
            ];
        ];
    ];
    Return[rules];
]
```

---

## 7. `genPRules` (line ~14372)

```mathematica
(* Generates pRules i.e. occupation operator for each site and occupation 
   by the element with site-operators *)
genPRules[numSites_, numElements_, siteOcSymbol_, siteOpSymbol_] := Module[
    {RMat, pRules, basisSymbolList, i, j, tempBasisSymbolList, tempList},
    basisSymbolList = Join[{1}, genBasisSymbolList[numElements, siteOpSymbol]];
    (* Print["basisSymbolList:", basisSymbolList]; *)
    RMat = genRMat[numElements];
    (* Print["RMat:", RMat]; *)
    pRules = {};
    For[i = 1, i <= numSites, i++,
        tempBasisSymbolList = {basisSymbolList[[1]]};
        For[j = 2, j <= Length[basisSymbolList], j++,
            tempBasisSymbolList = Append[tempBasisSymbolList, basisSymbolList[[j]][i]];
        ];
        (* Print["tempBasisSymbolList:", tempBasisSymbolList]; *)
        tempList = Simplify[RMat.tempBasisSymbolList];
        (* Print["tempList:", tempList]; *)
        For[j = 1, j <= numElements, j++,
            pRules = Append[pRules, siteOcSymbol[i][j] -> tempList[[j]]];
        ];
    ];
    (* Print["pRules:", pRules]; *)
    Return[pRules];
]
```

---

## 8. `tranClus` (line ~14489)

```mathematica
(* Returns a list of sites index for a given cluster clusCoord using maxClusSiteList *)
(* Transform cluster coordinate list into site number list *)
tranClus[clusCoord_, maxClusSiteList_] := Module[
    {i, j, clusSiteList},
    clusSiteList = {};
    For[i = 1, i <= Length[clusCoord], i++, (* Loop over sublattices *)
        clusSiteList = Append[clusSiteList, {}];
        For[j = 1, j <= Length[clusCoord[[i]]], j++, (* Loop over sites *)
            clusSiteList[[i]] = Append[clusSiteList[[i]],
                Position[maxClusSiteList, clusCoord[[i]][[j]][[1]]][[1]][[1]]
            ];
        ];
    ];
    Return[clusSiteList];
]
```

---

## 9. `groupSubClus` (line ~4700)

```mathematica
(* New Version: Generates all possible clusters of maxClusCoord with basisSymbolList 
   and classifies them according to cfData *)
groupSubClus[maxClusCoord_, cfData_, basisSymbolList_] := Module[
    {i, j, k, l, m, subClusCoordList, numClusCoord, classifiedSubClusList, cfOrbitList},
    cfOrbitList = cfData[[3]];
    classifiedSubClusList = {};
    For[i = 1, i <= Length[cfOrbitList], i++, (* loop over clusters *)
        classifiedSubClusList = Append[classifiedSubClusList, {}];
        For[j = 1, j <= Length[cfOrbitList[[i]]], j++, (* loop over hsp cf groups *)
            classifiedSubClusList[[i]] = Append[classifiedSubClusList[[i]], {}];
            For[k = 1, k <= Length[cfOrbitList[[i]][[j]]], k++,
                classifiedSubClusList[[i]][[j]] = Append[classifiedSubClusList[[i]][[j]], {}];
                For[m = 1, m <= Length[maxClusCoord], m++, (* loop over maximal clusters *)
                    subClusCoordList = genSubClusCoord[maxClusCoord[[m]], basisSymbolList];
                    (* Generating all possible subclusters of maxClusCoord[[m]] *)
                    For[l = 1, l <= Length[subClusCoordList], l++, (* Classifying subclusters *)
                        If[isContained[cfOrbitList[[i]][[j]][[k]], subClusCoordList[[l]]],
                            classifiedSubClusList[[i]][[j]][[k]] = 
                                Append[classifiedSubClusList[[i]][[j]][[k]], subClusCoordList[[l]]];
                        ];
                    ];
                ];
            ];
        ];
    ];
    Return[classifiedSubClusList];
]
```

---

## 10. `genCV` (line ~14640) — Main C-Matrix generation function

```mathematica
(* Steps:
   1. All the site-coordinates of maximal clusters will be assigned a site-operator symbol.
   2. List of substituteRules to be generated (product of site-operators -> CF).
   3. For each cluster:
      (a) convert cluster coordinates into site number list
      (b) all possible configurations to be generated
      (c) For each configuration:
          (i)   cluster-variable (CV) obtained in terms of products of site-occupation operator
          (ii)  site-occupation operator written in terms of site operators using rmat matrix
          (iii) lastly, CVs expressed in terms of CFs using substituteRules
      (d) Similar CVs grouped together to obtain wcv list and ncv
      (e) obtain cMat
*)
genCV[maxClusSiteList_, ordClusData_, ordCFData_, substituteRules_, pRules_, 
      numElements_, cfSymbol_, siteOcSymbol_, siteOpSymbol_] := Module[
    {i, j, k, l, configList, config, tempClusCoord, clusSiteList, tempCV, cvList, RMat,
     tempList, groupClusCoordList, ordClusCoordList, ncv, lcv, nwcv, wcv, uList, tallyCVList,
     tempCVList, tempArray, crow, tempCmat, cmat, basisSymbolList, numSites},
    
    ordClusCoordList = ordClusData[[1]];
    Print["maxClusSiteList:", maxClusSiteList];
    Print["pRules:", pRules];
    Print["substituteRules:", substituteRules];
    
    lcv = {};
    wcv = {};
    cmat = {};
    
    uList = Flatten[
        Table[
            Table[
                Table[cfSymbol[i][j][k],
                    {k, 1, Length[ordCFData[[1]][[i]][[j]]]}],
                {j, 1, Length[ordCFData[[1]][[i]]]}],
            {i, 1, Length[ordCFData[[1]]] - 1}
        ], 2];
    (* Print["uList:", uList]; *)
    
    For[i = 1, i <= Length[ordClusCoordList] - 1, i++, 
        (* loop running over hsp cluster groups except empty cluster *)
        lcv = Append[lcv, {}];
        wcv = Append[wcv, {}];
        tempCmat = {};
        
        For[j = 1, j <= Length[ordClusCoordList[[i]]], j++, 
            (* loop running over clusters of each hsp cluster group *)
            
            clusSiteList = Flatten[tranClus[ordClusCoordList[[i]][[j]], maxClusSiteList], 1];
            (* Print["clusSiteList:", clusSiteList]; *)
            
            configList = genConfig[ordClusCoordList[[i]][[j]], numElements];
            (* all possible configurations are generated *)
            
            tempCVList = {};
            For[k = 1, k <= Length[configList], k++, (* loop running over configurations *)
                config = configList[[k]];
                tempCV = 1;
                For[l = 1, l <= Length[config], l++,
                    tempCV = tempCV * siteOcSymbol[clusSiteList[[l]]][config[[l]]];
                ];
                (* Apply pRules then substituteRules *)
                tempCV = Expand[tempCV /. pRules] /. substituteRules;
                tempCVList = Append[tempCVList, tempCV];
            ];
            
            tallyCVList = Tally[tempCVList];
            ncv = Length[tallyCVList];
            nwcv = {};
            cvList = {};
            For[k = 1, k <= ncv, k++,
                cvList = Append[cvList, tallyCVList[[k]][[1]]];
                nwcv = Append[nwcv, tallyCVList[[k]][[2]]];
            ];
            
            lcv[[i]] = Append[lcv[[i]], ncv];
            wcv[[i]] = Append[wcv[[i]], nwcv];
            
            tempArray = CoefficientArrays[cvList, uList];
            crow = Join[tempArray[[2]], Partition[tempArray[[1]], {1}], 2] // Normal;
            tempCmat = Append[tempCmat, crow];
        ];
        cmat = Append[cmat, tempCmat];
    ];
    
    Return[{cmat, lcv, wcv}];
]
```

---

## 11. `genCMat` (line ~15309, commented out)

The `genCMat` call at line 15309 is **commented out** in the notebook:
```mathematica
(* cMatData = genCMat[Flatten[ordClusCoordList, 1], Flatten[ordClusOrbitList, 1], 2, {-1, 1}]; *)
```
No standalone definition of `genCMat` was found in the searched regions — it appears to be an older/alternative function that was replaced by `genCV`.

---

## 12. CMat Calculations — How the functions are called (line ~15224–15400)

```mathematica
(* Generating C-matrix, and classifying the data in the corresponding clusters of disordered phase *)

maxClusSiteList = genSiteList[maxClusCoord]; 
(* site coordinates of maximal cluster are assigned a site-number *)

groupCfCoordList = groupSubClus[maxClusCoord, cfData, basisSymbolList]; 
(* Generating all possible cfs of maxClusCoord with basisSymbolList and grouping *)

cfSiteOpList = genCfSiteOpList[groupCfCoordList, maxClusSiteList]; 
(* List of site operators of each cf *)

(* Old version commented out:
substituteRules = genSubstituteRules[maxClusCoord, maxClusSiteList, cfData, 
    basisSymbolList, cfSymbol, siteOpSymbol]; *)

substituteRules = genSubstituteRules[cfSiteOpList, cfSymbol];

numSites = Length[maxClusSiteList];

pRules = genPRules[numSites, numComp, siteOcSymbol, siteOpSymbol]; 
(* conventional basis has been applied here onwards *)

cMatData = genCV[maxClusSiteList, ordClusData, cfData, substituteRules, pRules, 
    numComp, cfSymbol, siteOcSymbol, siteOpSymbol];

(* Old genCMat call commented out:
cMatData = genCMat[Flatten[ordClusCoordList, 1], Flatten[ordClusOrbitList, 1], 2, {-1, 1}]; *)

wcv = cMatData[[3]];
lcv = cMatData[[2]];
cmat = cMatData[[1]];

Print["wcv:", wcv];
Print["lcv:", lcv];

uuList = Table[
    Table[
        Table[cfSymbol[i][j][k], {k, 1, lcf[[i]][[j]]}],
        {j, 1, Length[lcf[[i]]]}],
    {i, 1, Length[lcf]}];
uList = Flatten[uuList]

(* ... further computation of cv = cmat . uList ... *)
cv = Table[
    Table[cmat[[i]][[j]].uList, {j, 1, lc[[i]]}],
    {i, 1, tcdis}];
```

---

## Summary of Function Call Flow

```
1. genBasisSymbolList[numComp, siteOpSymbol]
     → produces {s[1], s[2], ..., s[numComp-1]}

2. groupSubClus[maxClusCoord, cfData, basisSymbolList]
     → classifies all subclusters of maxClusCoord according to cfData orbits
     → internally calls genSubClusCoord and isContained

3. genCfSiteOpList[groupCfCoordList, maxClusSiteList]
     → for each cf group, maps coordinates to site-operator symbols

4. genSubstituteRules[cfSiteOpList, cfSymbol]
     → generates rules: product-of-site-ops → cfSymbol[i][j][k]

5. genSiteList[maxClusCoord]
     → assigns unique site indices to all coordinates in maxClusCoord

6. genRMat[numElements]
     → R matrix = Inverse[M], where M is Vandermonde-like matrix from basis

7. genPRules[numSites, numElements, siteOcSymbol, siteOpSymbol]
     → generates rules: siteOcSymbol[i][j] → (R.basisVector)[[j]]
     → internally calls genRMat and genBasisSymbolList

8. genConfig[clusCoord, numElements]
     → generates all Tuples of {1,...,numElements} on cluster sites

9. tranClus[clusCoord, maxClusSiteList]
     → converts cluster coordinates to site index lists using Position

10. genCV[maxClusSiteList, ordClusData, ordCFData, substituteRules, 
         pRules, numElements, cfSymbol, siteOcSymbol, siteOpSymbol]
      → the main CMat generator: for each cluster and configuration,
        computes CVs, applies pRules then substituteRules,
        tallies unique CVs (wcv), builds coefficient matrix (cmat)
      → Returns {cmat, lcv, wcv}
```
