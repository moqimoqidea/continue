import { BranchAndDir, Chunk } from "../index.js";
import { FullTextSearchCodebaseIndex } from "../indexing/FullTextSearchCodebaseIndex.js";

export async function fullTextRetrieve(
  prefix: string,
  suffix: string,
  indexTag: BranchAndDir,
): Promise<Chunk[]> {
  const index = new FullTextSearchCodebaseIndex();
  const searchStrings = prefix.split("\n").slice(-3);
  const results: Chunk[] = [];
  searchStrings.forEach(async (searchString) => {
    const chunks = await index.retrieve(
      [indexTag],
      searchString,
      3,
      undefined,
      undefined,
    );
    results.push(...chunks);
  });
  return results;
}
