import { readFileSync, writeFileSync } from "node:fs";
import pkg from "@openfga/syntax-transformer";

const inputPath = process.argv[2];
const outputPath = process.argv[3];

if (!inputPath || !outputPath) {
  console.error("Usage: node tools/fga-transform.mjs <input.fga> <output.json>");
  process.exit(1);
}

const dsl = readFileSync(inputPath, "utf-8");
const json = JSON.parse(pkg.transformer.transformDSLToJSON(dsl));
writeFileSync(outputPath, JSON.stringify(json, null, 2) + "\n");
console.log(`Transformed ${inputPath} -> ${outputPath}`);
